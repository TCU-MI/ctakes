package org.apache.ctakes.temporal.ae;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.apache.ctakes.relationextractor.ae.RelationExtractorAnnotator;
import org.apache.ctakes.relationextractor.ae.features.RelationFeaturesExtractor;
import org.apache.ctakes.temporal.ae.feature.coreference.DistSemFeatureExtractor;
import org.apache.ctakes.temporal.ae.feature.coreference.DistanceFeatureExtractor;
import org.apache.ctakes.temporal.ae.feature.coreference.StringMatchingFeatureExtractor;
import org.apache.ctakes.temporal.ae.feature.coreference.TokenFeatureExtractor;
import org.apache.ctakes.temporal.ae.feature.coreference.UMLSFeatureExtractor;
import org.apache.ctakes.typesystem.type.relation.BinaryTextRelation;
import org.apache.ctakes.typesystem.type.relation.CoreferenceRelation;
import org.apache.ctakes.typesystem.type.relation.RelationArgument;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textsem.Markable;
import org.apache.ctakes.typesystem.type.textspan.Paragraph;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.cas.FloatArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.jcas.tcas.DocumentAnnotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.ml.CleartkAnnotator;
import org.cleartk.ml.DataWriter;
import org.cleartk.ml.jar.DefaultDataWriterFactory;
import org.cleartk.ml.jar.DirectoryDataWriterFactory;
import org.cleartk.ml.jar.GenericJarClassifierFactory;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.util.JCasUtil;

public class EventCoreferenceAnnotator extends RelationExtractorAnnotator {

  public static final int DEFAULT_SENT_DIST = 5;
  public static final String PARAM_SENT_DIST = "SentenceDistance";
  @ConfigurationParameter(name = PARAM_SENT_DIST, mandatory = false, description = "Number of sentences allowed between coreferent mentions")
  private int maxSentDist = DEFAULT_SENT_DIST;
  
  public static final double DEFAULT_PAR_SIM = 0.5;
  public static final String PARAM_PAR_SIM = "PararaphSimilarity";
  @ConfigurationParameter(name = PARAM_PAR_SIM, mandatory = false, description = "Similarity required to pair paragraphs for coreference")
  private double simThreshold = DEFAULT_PAR_SIM;
  
  public static AnalysisEngineDescription createDataWriterDescription(
      Class<? extends DataWriter<String>> dataWriterClass,
      File outputDirectory,
      float downsamplingRate) throws ResourceInitializationException {
    return AnalysisEngineFactory.createEngineDescription(
        EventCoreferenceAnnotator.class,
        CleartkAnnotator.PARAM_IS_TRAINING,
        true,
        RelationExtractorAnnotator.PARAM_PROBABILITY_OF_KEEPING_A_NEGATIVE_EXAMPLE,
        downsamplingRate,
        DefaultDataWriterFactory.PARAM_DATA_WRITER_CLASS_NAME,
        dataWriterClass,
        DirectoryDataWriterFactory.PARAM_OUTPUT_DIRECTORY,
        outputDirectory);
  }
  
  public static AnalysisEngineDescription createAnnotatorDescription(File modelDirectory)
      throws ResourceInitializationException {
    return AnalysisEngineFactory.createEngineDescription(
        EventCoreferenceAnnotator.class,
        CleartkAnnotator.PARAM_IS_TRAINING,
        false,
        GenericJarClassifierFactory.PARAM_CLASSIFIER_JAR_PATH,
        new File(modelDirectory, "model.jar"));
  }
  
  @Override
  protected List<RelationFeaturesExtractor> getFeatureExtractors() {
    List<RelationFeaturesExtractor> featureList = new ArrayList<>();
    
    featureList.addAll(super.getFeatureExtractors());
        
    featureList.add(new DistanceFeatureExtractor());
    featureList.add(new StringMatchingFeatureExtractor());
    featureList.add(new TokenFeatureExtractor());
    featureList.add(new UMLSFeatureExtractor());
    try{
      featureList.add(new DistSemFeatureExtractor());
    }catch(IOException e){
      e.printStackTrace();
    }
    return featureList;
  }
  

  @Override
  protected List<IdentifiedAnnotationPair> getCandidateRelationArgumentPairs(
      JCas jcas, Annotation docAnnotation) {
    List<Markable> markables = new ArrayList<>(JCasUtil.select(jcas, Markable.class));
//    List<Markable> markables = JCasUtil.selectCovered(Markable.class, docAnnotation);
    List<IdentifiedAnnotationPair> pairs = new ArrayList<>();
    
//    // CODE FOR SENTENCE-DISTANCE-LIMITED PAIR MATCHING
//    for(int i = 1; i < markables.size(); i++){
//      for(int j = i-1; j >= 0; j--){
//        IdentifiedAnnotation ante = markables.get(j);
//        IdentifiedAnnotation ana = markables.get(i);
//        int sentdist = sentDist(jcas, ante, ana);
//        if(sentdist > maxSentDist) break;
//        pairs.add(new IdentifiedAnnotationPair(ante, ana));
//      }
//    }
    
    FSArray parVecs = JCasUtil.selectSingle(jcas, FSArray.class);
    
    // CODE FOR PARAGRAPH-BASED MATCHING
    List<Paragraph> pars = new ArrayList<>(JCasUtil.select(jcas, Paragraph.class));
    double[][] sims = new double[pars.size()][pars.size()];
    for(int i = 0; i < sims.length; i++){
      Arrays.fill(sims[i], 0.0);
    }
    
    for(int i = 0; i < pars.size(); i++){
      // get all pairs within this paragraph
      List<Markable> curParMarkables = JCasUtil.selectCovered(Markable.class, pars.get(i));
      for(int anaId = 1; anaId < curParMarkables.size(); anaId++){
        for(int anteId = anaId-1; anteId >= 0; anteId--){
          Markable ana = curParMarkables.get(anaId);
          Markable ante = curParMarkables.get(anteId);
          int sentdist = sentDist(jcas, ante, ana);
          if(sentdist > maxSentDist) break;
          pairs.add(new IdentifiedAnnotationPair(ante, ana));
        }
      }
      
      // now get all pairs between markables in this paragraph and others
      FloatArray parVec = (FloatArray) parVecs.get(i);
      for(int j = i-1; j >= 0; j--){
        if(sims[i][j] == 0.0){
          // compute the sim explicitly
          FloatArray prevParVec = (FloatArray) parVecs.get(j);
          sims[i][j] = calculateSimilarity(parVec, prevParVec);
        }
        
        if(sims[i][j] > simThreshold){
          // pair up all markables in each paragraph
          List<Markable> prevParMarkables = JCasUtil.selectCovered(Markable.class, pars.get(j));
          for(int anaId = 0; anaId < curParMarkables.size(); anaId++){
            for(int anteId = prevParMarkables.size()-1; anteId >= 0; anteId--){
              Markable ana = curParMarkables.get(anaId);
              Markable ante = prevParMarkables.get(anteId);
              int sentdist = sentDist(jcas, ante, ana);
              if(sentdist > maxSentDist) break;
              pairs.add(new IdentifiedAnnotationPair(ante, ana));
            }
          }
        }
      }
    }
    return pairs;
  }

  @Override
  protected Class<? extends Annotation> getCoveringClass() {
    return DocumentAnnotation.class;
  }
  
  @Override
  protected Class<? extends BinaryTextRelation> getRelationClass() {
    return CoreferenceRelation.class;
  }

  protected HashSet<IdentifiedAnnotation> foundAnaphors = new HashSet<>(); 
  
  @Override
  protected void createRelation(
      JCas jCas,
      IdentifiedAnnotation ante,
      IdentifiedAnnotation ana,
      String predictedCategory) {
    // check if its already been linked
    if(!foundAnaphors.contains(ana)){
      // add the relation to the CAS
      RelationArgument relArg1 = new RelationArgument(jCas);
      relArg1.setArgument(ante);
      relArg1.setRole("Antecedent");
      relArg1.addToIndexes();
      RelationArgument relArg2 = new RelationArgument(jCas);
      relArg2.setArgument(ana);
      relArg2.setRole("Anaphor");
      relArg2.addToIndexes();
      CoreferenceRelation relation = new CoreferenceRelation(jCas);
      relation.setArg1(relArg1);
      relation.setArg2(relArg2);
      relation.setCategory(predictedCategory);
      relation.addToIndexes();
      foundAnaphors.add(ana);
    }
  }
  
  private static int sentDist(JCas jcas, IdentifiedAnnotation arg1,
      IdentifiedAnnotation arg2) {
    Collection<Sentence> sents = JCasUtil.selectCovered(jcas, Sentence.class, arg1.getBegin(), arg2.getEnd());
    return sents.size();
  }
  
  private static double calculateSimilarity(FloatArray f1, FloatArray f2){
    double sim = 0.0f;
    double f1len = 0.0;
    double f2len = 0.0;
    
    for(int i = 0; i < f1.size(); i++){
      sim += (f1.get(i) * f2.get(i));
      f1len += (f1.get(i) * f1.get(i));
      f2len += (f2.get(i) * f2.get(i));
    }
    f1len = Math.sqrt(f1len);
    f2len = Math.sqrt(f2len);
    sim = sim / (f1len * f2len);
    
    return sim;
  }
}
