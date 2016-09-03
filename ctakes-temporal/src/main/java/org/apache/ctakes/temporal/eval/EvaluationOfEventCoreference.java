package org.apache.ctakes.temporal.eval;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.apache.ctakes.core.resource.FileLocator;
import org.apache.ctakes.core.util.DocumentIDAnnotationUtil;
import org.apache.ctakes.relationextractor.eval.RelationExtractorEvaluation.HashableArguments;
import org.apache.ctakes.temporal.ae.CoreferenceChainAnnotator;
import org.apache.ctakes.temporal.ae.EventCoreferenceAnnotator;
import org.apache.ctakes.temporal.eval.EvaluationOfEventTimeRelations.ParameterSettings;
import org.apache.ctakes.typesystem.type.relation.BinaryTextRelation;
import org.apache.ctakes.typesystem.type.relation.CollectionTextRelation;
import org.apache.ctakes.typesystem.type.relation.CoreferenceRelation;
import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.ctakes.typesystem.type.syntax.NewlineToken;
import org.apache.ctakes.typesystem.type.syntax.WordToken;
import org.apache.ctakes.typesystem.type.textsem.Markable;
import org.apache.ctakes.typesystem.type.textspan.Paragraph;
import org.apache.ctakes.utils.distsem.WordEmbeddings;
import org.apache.ctakes.utils.distsem.WordVector;
import org.apache.ctakes.utils.distsem.WordVectorReader;
import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.pipeline.JCasIterator;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.cas.FSList;
import org.apache.uima.jcas.cas.FloatArray;
import org.apache.uima.jcas.cas.NonEmptyFSList;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.FileUtils;
import org.cleartk.eval.AnnotationStatistics;
import org.cleartk.ml.jar.JarClassifierBuilder;
import org.cleartk.ml.liblinear.LibLinearStringOutcomeDataWriter;
import org.cleartk.ml.tksvmlight.model.CompositeKernel.ComboOperator;
import org.cleartk.util.ViewUriUtil;

import com.google.common.base.Function;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.lexicalscope.jewel.cli.CliFactory;
import com.lexicalscope.jewel.cli.Option;

public class EvaluationOfEventCoreference extends EvaluationOfTemporalRelations_ImplBase {
 
  static interface CoreferenceOptions extends TempRelOptions{
    @Option
    public String getOutputDirectory();
    
    @Option
    public boolean getUseTmp();
    
  }
  
  public static float COREF_DOWNSAMPLE = 0.5f;
  protected static ParameterSettings allParams = new ParameterSettings(DEFAULT_BOTH_DIRECTIONS, COREF_DOWNSAMPLE, "tk",
      1.0, 1.0, "linear", ComboOperator.SUM, 0.1, 0.5);  // (0.3, 0.4 for tklibsvm)

  public static void main(String[] args) throws Exception {
    CoreferenceOptions options = CliFactory.parseArguments(CoreferenceOptions.class, args);

    List<Integer> patientSets = options.getPatients().getList();
    List<Integer> trainItems = getTrainItems(options);
    List<Integer> testItems = getTestItems(options);

    ParameterSettings params = allParams;
    File workingDir = new File("target/eval/temporal-relations/coreference");
    if(!workingDir.exists()) workingDir.mkdirs();
    if(options.getUseTmp()){
      File tempModelDir = File.createTempFile("temporal", null, workingDir);
      tempModelDir.delete();
      tempModelDir.mkdir();
      workingDir = tempModelDir;
    }
    EvaluationOfEventCoreference eval = new EvaluationOfEventCoreference(
        workingDir,
        options.getRawTextDirectory(),
        options.getXMLDirectory(),
        options.getXMLFormat(),
        options.getXMIDirectory(),
        options.getTreebankDirectory(),
        options.getCoreferenceDirectory(),
        options.getPrintErrors(),
        options.getPrintFormattedRelations(),
        params,
        options.getKernelParams(),
        options.getOutputDirectory());

    eval.prepareXMIsFor(patientSets);

    params.stats = eval.trainAndTest(trainItems, testItems);//training);//
    //      System.err.println(options.getKernelParams() == null ? params : options.getKernelParams());
    System.err.println(params.stats);

    if(options.getUseTmp()){
      FileUtils.deleteRecursive(workingDir);
    }
  }
  
  private String outputDirectory;
  
  public EvaluationOfEventCoreference(File baseDirectory,
      File rawTextDirectory, File xmlDirectory,
      org.apache.ctakes.temporal.eval.Evaluation_ImplBase.XMLFormat xmlFormat,
      File xmiDirectory, File treebankDirectory, File coreferenceDirectory, boolean printErrors,
      boolean printRelations, ParameterSettings params, String cmdParams, String outputDirectory) {
    super(baseDirectory, rawTextDirectory, xmlDirectory, xmlFormat, xmiDirectory,
        treebankDirectory, coreferenceDirectory, printErrors, printRelations, params);
    this.outputDirectory = outputDirectory;
    this.kernelParams = cmdParams == null ? null : cmdParams.split(" ");
  }

  @Override
  protected void train(CollectionReader collectionReader, File directory)
      throws Exception {
    AggregateBuilder aggregateBuilder = this.getPreprocessorAggregateBuilder();
    aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(DocumentIDPrinter.class));
    aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(ParagraphAnnotator.class));
    aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(ParagraphVectorAnnotator.class));
    aggregateBuilder.add(CopyFromGold.getDescription(Markable.class, CoreferenceRelation.class, CollectionTextRelation.class));
    aggregateBuilder.add(EventCoreferenceAnnotator.createDataWriterDescription(
//        TKSVMlightStringOutcomeDataWriter.class,
        LibLinearStringOutcomeDataWriter.class,
        directory,
        params.probabilityOfKeepingANegativeExample
        ));
    // create gold chains for writing out which we can then use for our scoring tool
//    aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(CoreferenceChainScoringOutput.class,
//        CoreferenceChainScoringOutput.PARAM_OUTPUT_DIR,
//        this.outputDirectory + "train"));
    SimplePipeline.runPipeline(collectionReader, aggregateBuilder.createAggregate());
    String[] optArray;

    if(this.kernelParams == null){
      ArrayList<String> svmOptions = new ArrayList<>();
      svmOptions.add("-c"); svmOptions.add(""+params.svmCost);        // svm cost
      svmOptions.add("-t"); svmOptions.add(""+params.svmKernelIndex); // kernel index 
      svmOptions.add("-d"); svmOptions.add("3");                      // degree parameter for polynomial
      svmOptions.add("-g"); svmOptions.add(""+params.svmGamma);
      if(params.svmKernelIndex==ParameterSettings.SVM_KERNELS.indexOf("tk")){
        svmOptions.add("-S"); svmOptions.add(""+params.secondKernelIndex);   // second kernel index (similar to -t) for composite kernel
        String comboFlag = (params.comboOperator == ComboOperator.SUM ? "+" : params.comboOperator == ComboOperator.PRODUCT ? "*" : params.comboOperator == ComboOperator.TREE_ONLY ? "T" : "V");
        svmOptions.add("-C"); svmOptions.add(comboFlag);
        svmOptions.add("-L"); svmOptions.add(""+params.lambda);
        svmOptions.add("-T"); svmOptions.add(""+params.tkWeight);
        svmOptions.add("-N"); svmOptions.add("3");   // normalize trees and features
      }
      optArray = svmOptions.toArray(new String[]{});
    }else{
      optArray = this.kernelParams;
      for(int i = 0; i < optArray.length; i+=2){
        optArray[i] = "-" + optArray[i];
      }
    }
    JarClassifierBuilder.trainAndPackage(directory, optArray);
  }

  @Override
  protected AnnotationStatistics<String> test(
      CollectionReader collectionReader, File directory) throws Exception {
    AggregateBuilder aggregateBuilder = this.getPreprocessorAggregateBuilder();
    aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(DocumentIDPrinter.class));
    aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(ParagraphAnnotator.class));
    aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(ParagraphVectorAnnotator.class));
    aggregateBuilder.add(CopyFromGold.getDescription(Markable.class));
    aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(CoreferenceChainScoringOutput.class,
        CoreferenceChainScoringOutput.PARAM_OUTPUT_FILENAME,
        this.outputDirectory + "gold.chains",
        CoreferenceChainScoringOutput.PARAM_USE_GOLD_CHAINS,
        true));
    aggregateBuilder.add(EventCoreferenceAnnotator.createAnnotatorDescription(directory));
    aggregateBuilder.add(CoreferenceChainAnnotator.createAnnotatorDescription());
    aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(CoreferenceChainScoringOutput.class,
        CoreferenceChainScoringOutput.PARAM_OUTPUT_FILENAME,
        this.outputDirectory + "system.chains"));

    Function<CoreferenceRelation, ?> getSpan = new Function<CoreferenceRelation, HashableArguments>() {
      public HashableArguments apply(CoreferenceRelation relation) {
        return new HashableArguments(relation);
      }
    };
    Function<CoreferenceRelation, String> getOutcome = new Function<CoreferenceRelation,String>() {
      public String apply(CoreferenceRelation relation){
        return "Coreference";
      }
    };
    AnnotationStatistics<String> stats = new AnnotationStatistics<>();

    for(Iterator<JCas> casIter =new JCasIterator(collectionReader, aggregateBuilder.createAggregate()); casIter.hasNext();){
      JCas jCas = casIter.next();
      JCas goldView = jCas.getView(GOLD_VIEW_NAME);
      JCas systemView = jCas.getView(CAS.NAME_DEFAULT_SOFA);
      Collection<CoreferenceRelation> goldRelations = JCasUtil.select(
          goldView,
          CoreferenceRelation.class);
      Collection<CoreferenceRelation> systemRelations = JCasUtil.select(
          systemView,
          CoreferenceRelation.class);
      stats.add(goldRelations, systemRelations, getSpan, getOutcome);
      if(this.printErrors){
        Map<HashableArguments, BinaryTextRelation> goldMap = Maps.newHashMap();
        for (BinaryTextRelation relation : goldRelations) {
          goldMap.put(new HashableArguments(relation), relation);
        }
        Map<HashableArguments, BinaryTextRelation> systemMap = Maps.newHashMap();
        for (BinaryTextRelation relation : systemRelations) {
          systemMap.put(new HashableArguments(relation), relation);
        }
        Set<HashableArguments> all = Sets.union(goldMap.keySet(), systemMap.keySet());
        List<HashableArguments> sorted = Lists.newArrayList(all);
        Collections.sort(sorted);
        for (HashableArguments key : sorted) {
          BinaryTextRelation goldRelation = goldMap.get(key);
          BinaryTextRelation systemRelation = systemMap.get(key);
          if (goldRelation == null) {
            System.out.println("System added: " + formatRelation(systemRelation));
          } else if (systemRelation == null) {
            System.out.println("System dropped: " + formatRelation(goldRelation));
          } else if (!systemRelation.getCategory().equals(goldRelation.getCategory())) {
            String label = systemRelation.getCategory();
            System.out.printf("System labeled %s for %s\n", label, formatRelation(goldRelation));
          } else{
            System.out.println("Nailed it! " + formatRelation(systemRelation));
          }
        }
      }
    }

    return stats;
  }

  public static class CoreferenceChainScoringOutput extends org.apache.uima.fit.component.JCasAnnotator_ImplBase {
    public static final String PARAM_OUTPUT_FILENAME = "OutputDirectory";
    @ConfigurationParameter(
        name = PARAM_OUTPUT_FILENAME,
        mandatory = true,
        description = "Directory to write output"
        )
    private String outputFilename;
    private PrintWriter out = null;
    
    public static final String PARAM_USE_GOLD_CHAINS = "UseGoldChains";
    @ConfigurationParameter(
        name = PARAM_USE_GOLD_CHAINS,
        mandatory = false,
        description = "Whether to use gold chains for writing output"
        )
    private boolean useGoldChains = false;
    
    @Override
    public void initialize(final UimaContext context) throws ResourceInitializationException{
      super.initialize(context);
      
      try {
        out = new PrintWriter(outputFilename);
      } catch (FileNotFoundException e) {
        e.printStackTrace();
        throw new ResourceInitializationException(e);
      }
    }
    
    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
      File filename = new File(ViewUriUtil.getURI(jCas));
      JCas chainsCas = null;
      try {
         chainsCas = useGoldChains? jCas.getView(GOLD_VIEW_NAME) : jCas;
      } catch (CASException e) {
        e.printStackTrace();
        throw new AnalysisEngineProcessException(e);
      }
      int chainNum = 1;
      HashMap<Annotation, Integer> ent2chain = new HashMap<>();
      if(useGoldChains) System.out.println("Gold chains:");
      else System.out.println("System chains:");
      for(CollectionTextRelation chain : JCasUtil.select(chainsCas, CollectionTextRelation.class)){
        FSList members = chain.getMembers();
        while(members instanceof NonEmptyFSList){
          Annotation mention = (Annotation) ((NonEmptyFSList) members).getHead();
          ent2chain.put(mention, chainNum);
          members = ((NonEmptyFSList)members).getTail();
          System.out.print("Mention: " + mention.getCoveredText());
          System.out.print(" (" + mention.getBegin() + ", " + mention.getEnd() + ")");
          System.out.print("  ----->    ");
        }
        System.out.println();
        chainNum++;
      }
      
      out.println("#begin document " + filename.getPath());
      List<BaseToken> tokens = new ArrayList<>(JCasUtil.select(jCas, BaseToken.class));
      Stack<Integer> endStack = new Stack<>();
      for(int i = 0; i < tokens.size(); i++){
        BaseToken token = tokens.get(i);
        List<Markable> markables = new ArrayList<>(JCasUtil.selectCovering(chainsCas, Markable.class, token.getBegin(), token.getEnd()));
        List<Integer> startMention = new ArrayList<>();
        Multiset<Integer> endMention = HashMultiset.create();
        List<Integer> wholeMention = new ArrayList<>();
        
        for(Annotation markable : markables){
          if(ent2chain.containsKey(markable)){
            if(markable.getBegin() == token.getBegin()){
              if(markable.getEnd() == token.getEnd()){
                wholeMention.add(ent2chain.get(markable));
              }else{
                startMention.add(ent2chain.get(markable));
              }
            }else if(markable.getEnd() <= token.getEnd()){
              if(endMention.contains(ent2chain.get(markable))){
                System.err.println("There is a duplicate element -- should be handled by multiset");
              }
              if(markable.getEnd() < token.getEnd()){
                System.err.println("There is a markable that ends in the middle of a token!");
              }
              endMention.add(ent2chain.get(markable));
            }
          }
        }
        out.print(i+1);
        out.print('\t');
        StringBuffer buff = new StringBuffer();
        while(endStack.size() > 0 && endMention.contains(endStack.peek())){
          int ind = endStack.pop();
          buff.append(ind);
          buff.append(')');
          buff.append('|');
          endMention.remove(ind);
        }
        for(int ind : wholeMention){
          buff.append('(');
          buff.append(ind);
          buff.append(')');
          buff.append('|');
        }
        for(int ind : startMention){
          buff.append('(');
          buff.append(ind);
          buff.append('|');
          endStack.push(ind);
        }
//        for(int ind : endMention){
//          buff.append(ind);
//          buff.append(')');
//          buff.append('|');
//        }
        if(buff.length() > 0){
          out.println(buff.substring(0,  buff.length()-1));
        }else{
          out.println("_");
        }
      }
      out.println("#end document " + filename.getPath());
      out.println();
    }
  }
  
  public static class AnnotationComparator implements Comparator<Annotation> {

    @Override
    public int compare(Annotation o1, Annotation o2) {
      if(o1.getBegin() < o2.getBegin()){
        return -1;
      }else if(o1.getBegin() == o2.getBegin() && o1.getEnd() < o2.getEnd()){
        return -1;
      }else if(o1.getBegin() == o2.getBegin() && o1.getEnd() > o2.getEnd()){
        return 1;
      }else if(o2.getBegin() < o1.getBegin()){
        return 1;
      }else{
        return 0;
      }
    }
  }
  public static class DocumentIDPrinter extends org.apache.uima.fit.component.JCasAnnotator_ImplBase {
    static Logger logger = Logger.getLogger(DocumentIDPrinter.class);
    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
      String docId = DocumentIDAnnotationUtil.getDocumentID(jCas);
      if(docId == null){
        docId = new File(ViewUriUtil.getURI(jCas)).getName();
      }
      logger.info(String.format("Processing %s\n", docId));
    }
    
  }
  
  public static class ParagraphAnnotator extends org.apache.uima.fit.component.JCasAnnotator_ImplBase {

    @Override
    public void process(JCas jcas) throws AnalysisEngineProcessException {
      List<BaseToken> tokens = new ArrayList<>(JCasUtil.select(jcas, BaseToken.class));
      BaseToken lastToken = null;
      int parStart = 0;
      
      for(int i = 0; i < tokens.size(); i++){
        BaseToken token = tokens.get(i);
        if(parStart == i && token instanceof NewlineToken){
          // we've just created a pargraph ending but there were multiple newlines -- don't want to start the
          // new paragraph until we are past the newlines -- increment the parStart index and move forward
          parStart++;
        }else if(lastToken != null && token instanceof NewlineToken){
          Paragraph par = new Paragraph(jcas, tokens.get(parStart).getBegin(), lastToken.getEnd());
          par.addToIndexes();
          parStart = i+1;
        }
        lastToken = token;
      }
      
    }
    
  }
  
  public static class ParagraphVectorAnnotator extends org.apache.uima.fit.component.JCasAnnotator_ImplBase {
    WordEmbeddings words = null;

    @Override
    public void initialize(final UimaContext context) throws ResourceInitializationException{
      try {
        words = WordVectorReader.getEmbeddings(FileLocator.getAsStream("org/apache/ctakes/coreference/distsem/mimic_vectors.txt"));
      } catch (IOException e) {
        e.printStackTrace();
        throw new ResourceInitializationException(e);
      }
    }
    
    @Override
    public void process(JCas jcas) throws AnalysisEngineProcessException {
      List<Paragraph> pars = new ArrayList<>(JCasUtil.select(jcas, Paragraph.class));
      FSArray parVecs = new FSArray(jcas, pars.size());
      for(int parNum = 0; parNum < pars.size(); parNum++){
        Paragraph par = pars.get(parNum);
        float[] parVec = new float[words.getDimensionality()];

        List<BaseToken> tokens = JCasUtil.selectCovered(BaseToken.class, par);
        for(int i = 0; i < tokens.size(); i++){
          BaseToken token = tokens.get(i);
          if(token instanceof WordToken){
            String word = token.getCoveredText().toLowerCase();
            if(words.containsKey(word)){
              WordVector wv = words.getVector(word);
              for(int j = 0; j < parVec.length; j++){
                parVec[j] += wv.getValue(j);
              }
            }          
          }
        }
        normalize(parVec);
        FloatArray vec = new FloatArray(jcas, words.getDimensionality());
        vec.copyFromArray(parVec, 0, 0, parVec.length);
        vec.addToIndexes();
        parVecs.set(parNum, vec);
      }
      parVecs.addToIndexes();
    }

    private static final void normalize(float[] vec) {
      double sum = 0.0;
      for(int i = 0; i < vec.length; i++){
        sum += (vec[i]*vec[i]);
      }
      sum = Math.sqrt(sum);
      for(int i = 0; i < vec.length; i++){
        vec[i] /= sum;
      }
    }
  }
}
