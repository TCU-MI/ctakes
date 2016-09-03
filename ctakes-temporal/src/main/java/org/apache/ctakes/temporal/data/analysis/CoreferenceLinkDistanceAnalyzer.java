package org.apache.ctakes.temporal.data.analysis;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.ctakes.core.resource.FileLocator;
import org.apache.ctakes.temporal.eval.EvaluationOfEventCoreference.ParagraphAnnotator;
import org.apache.ctakes.temporal.eval.Evaluation_ImplBase.XMIReader;
import org.apache.ctakes.typesystem.type.relation.CollectionTextRelation;
import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.ctakes.typesystem.type.syntax.WordToken;
import org.apache.ctakes.typesystem.type.textsem.Markable;
import org.apache.ctakes.typesystem.type.textspan.Paragraph;
import org.apache.ctakes.utils.distsem.WordEmbeddings;
import org.apache.ctakes.utils.distsem.WordVector;
import org.apache.ctakes.utils.distsem.WordVectorReader;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.pipeline.JCasIterator;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSList;
import org.apache.uima.jcas.cas.NonEmptyFSList;
import org.cleartk.util.ViewUriUtil;
import org.cleartk.util.ae.UriToDocumentTextAnnotator;
import org.cleartk.util.cr.UriCollectionReader;

import com.lexicalscope.jewel.cli.CliFactory;
import com.lexicalscope.jewel.cli.Option;

public class CoreferenceLinkDistanceAnalyzer {
  static interface Options {

    @Option(
        shortName = "i",
        description = "specify the path to the directory containing the text files")
    public File getInputDirectory();
    
    @Option(
        shortName = "x",
        description = "Specify the path to the directory containing the xmis")
    public File getXMIDirectory();
  }
  
  public static final String GOLD_VIEW_NAME = "GoldView";
  
  public static void main(String[] args) throws UIMAException, IOException {
    Options options = CliFactory.parseArguments(Options.class, args);
    CollectionReader reader = UriCollectionReader.getCollectionReaderFromFiles(getFiles(options.getInputDirectory(), options.getXMIDirectory()));
    AggregateBuilder aggregateBuilder = new AggregateBuilder();
    aggregateBuilder.add(UriToDocumentTextAnnotator.getDescription());
    aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(
        XMIReader.class,
        XMIReader.PARAM_XMI_DIRECTORY,
        options.getXMIDirectory()));
    aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(ParagraphAnnotator.class));
    
    WordEmbeddings words = WordVectorReader.getEmbeddings(FileLocator.getAsStream("org/apache/ctakes/coreference/distsem/mimic_vectors.txt"));

    double[] parVec = new double[words.getDimensionality()];
    Arrays.fill(parVec, 0.0);
    int numWords = 0;
    double[] thresholds = {0.1, 0.25, 0.5, 0.75};
    int[][] thresholdSavings = new int[thresholds.length][2];
    double[] recalls = new double[thresholds.length];
    int numDocs = 0;
    
    // compute paragraph vectors for every paragraph
    AnalysisEngine ae = aggregateBuilder.createAggregate();
    
    for(Iterator<JCas> casIter = new JCasIterator(reader, ae); casIter.hasNext();){
      JCas jcas = casIter.next();
      numDocs++;
      // print out document name
      System.out.println("######### Document id: " + ViewUriUtil.getURI(jcas).toString());
      JCas goldView = jcas.getView(GOLD_VIEW_NAME);
      
      Map<Markable,Integer> markable2par = new HashMap<>();
      List<double[]> vectors = new ArrayList<>();
      
      for(Paragraph par : JCasUtil.select(jcas, Paragraph.class)){
        // map markables to paragraph numbers
        Collection<Markable> markables = JCasUtil.selectCovered(goldView, Markable.class, par);
        for(Markable markable : markables){
          markable2par.put(markable, vectors.size());
        }

        // build embedding vector for this paragraph
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
        vectors.add(parVec);
        parVec = new double[words.getDimensionality()];
        Arrays.fill(parVec, 0.0);        
      }
        
        
//      List<BaseToken> tokens = new ArrayList<>(JCasUtil.select(jcas, BaseToken.class));
      
//      BaseToken lastToken = null;
//      int parStart = 0;
//      for(int i = 0; i < tokens.size(); i++){
//        BaseToken token = tokens.get(i);
//        if(token instanceof WordToken){
//          String word = token.getCoveredText().toLowerCase();
//          if(words.containsKey(word)){
//            numWords++;
//            WordVector wv = words.getVector(word);
//            for(int j = 0; j < parVec.length; j++){
//              parVec[j] += wv.getValue(j);
//            }
//          }
//        }else if(lastToken != null && lastToken instanceof NewlineToken && token instanceof NewlineToken){
//          if(numWords > 0){
//            int parEnd = token.getEnd();
//            Collection<Markable> markables = JCasUtil.selectCovered(goldView, Markable.class, parStart, parEnd);
//            for(Markable markable : markables){
//              markable2par.put(markable, vectors.size());
//            }
//            Paragraph par = new Paragraph(jcas, parStart, parEnd);
//            normalize(parVec);
//            vectors.add(parVec);
//            parVec = new double[words.getDimensionality()];
//            Arrays.fill(parVec, 0.0);
//            numWords = 0;
//            parStart = parEnd;
//          }
//        }
//        lastToken = token;
//      }

      double[][] sims = new double[vectors.size()][vectors.size()];
      // compute similarities between every pair of vectors
      for(int i = 0; i < vectors.size(); i++){
        sims[i][i] = 1.0;
        for(int j = i+1; j< vectors.size(); j++){
          double sim = getSimilarity(vectors.get(i), vectors.get(j));
          sims[i][j] = sim;
          for(int ind = 0; ind < thresholds.length; ind++){
            if(sim < thresholds[ind]){
              thresholdSavings[ind][0]++;
            }
            thresholdSavings[ind][1]++;
          }
          System.out.printf("Similarity between paragraphs %d and %d = %f\n", i, j, sim);
        }
      }

      // build markable chains in easier to access way
      List<List<Integer>> parChains = new ArrayList<>();
      for(CollectionTextRelation chain : JCasUtil.select(goldView, CollectionTextRelation.class)){
        Set<Integer> pars = new HashSet<>();
        
        FSList list = chain.getMembers();
        while(list instanceof NonEmptyFSList){
          Markable member = (Markable) ((NonEmptyFSList) list).getHead();
          if(markable2par.containsKey(member)){
            pars.add(markable2par.get(member));
          }else{
            System.err.println("Markable not found in any paragraph: " + member.getCoveredText() + " [" + member.getBegin() + "," + member.getEnd() + "]");
          }
          list = ((NonEmptyFSList) list).getTail();
        }
        if(pars.size() > 1){
          List<Integer> parList = new ArrayList<>(pars);
          Collections.sort(parList);
          parChains.add(parList);
        }
      }
      
      for(int i = 0; i < thresholds.length; i++){
        double threshold = thresholds[i];
        int tps = 0;
        int fns = 0;
        
        // figure out our leakage rate:
        for(List<Integer> chain : parChains){
          // for any paragraph with an anaphor, look at all the earlier paragraphs
          // with antecedents
          for(int anaParInd = 1; anaParInd < chain.size(); anaParInd++){
            int anteParInd = 0;
            for(anteParInd = 0; anteParInd < anaParInd; anteParInd++){
              int anaPar = chain.get(anaParInd);
              int antePar = chain.get(anteParInd);
              // if any of the previous paragraphs has an antecedent we are ok
              if(sims[antePar][anaPar] > threshold){
                tps++;
                break;
              }
            }
            // if we got to the exit condition of the for-loop we didn't
            // have any matching paragraphs with high enough similarity
            if(anteParInd == anaParInd){
              fns++;
            }
          }
//        for(int focusPar = vectors.size()-1; focusPar >= 0; focusPar--){
//          for(int otherPar = 0; otherPar < focusPar; otherPar++){
//            double sim = sims[otherPar][focusPar];
//            for(List<Integer> chain : parChains){
//              for(int ind = chain.size()-1; ind > 0; ind--){
//                int anaPar = chain.get(ind);
//                if(focusPar == anaPar){
//                  // see if there are antecedents in any of the threshold-passing paragraphs
//                  int prev;
//                  for(prev = ind-1; prev >= 0; prev--){
//                    int antePar = chain.get(prev);
//                    if(sim > threshold && antePar == otherPar){
//                      hits++;
//                      break;
//                    }
//                  }
//                  if(prev < 0){
//                    misses++;
//                  }
//                }
//              }
//            }
//          }
        }
        double recall = (double) tps / (tps + fns);
        recalls[i] += recall;
        System.out.printf("With threshold %f, recall is %f with %d hits and %d misses\n", threshold, recall, tps, fns);
      }
      
      System.out.println("\n\n");
    }
    
    for(int i = 0; i < thresholds.length; i++){
      System.out.printf("Threshold %f has average recall %f\n", thresholds[i], recalls[i] / numDocs);
      System.out.printf("Was able to ignore %d pairs out of %d possible pairs\n", thresholdSavings[i][0], thresholdSavings[i][1]);
    }
  }
  
  public static final void normalize(double[] vec){
    double sum = 0.0;
    for(int i = 0; i < vec.length; i++){
      sum += (vec[i]*vec[i]);
    }
    sum = Math.sqrt(sum);
    for(int i = 0; i < vec.length; i++){
      vec[i] /= sum;
    }
  }
  
  private static final double getSimilarity(double[] v1, double[] v2){
    assert v1.length == v2.length;
    double sim = 0;
    double v1norm=0, v2norm=0;
    for(int i = 0; i < v1.length; i++){
      sim += (v1[i] * v2[i]);
      v1norm += (v1[i]*v1[i]);
      v2norm += (v2[i]*v2[i]);
    }
    v1norm = Math.sqrt(v1norm);
    v2norm = Math.sqrt(v2norm);
    
    sim = sim / (v1norm * v2norm);
    return sim;
  }
  
  public static Collection<File> getFiles(File textDir, File xmiDir){
    Collection<File> files = new HashSet<>();
    
    File[] xmiFiles = xmiDir.listFiles(new FilenameFilter(){

      public boolean accept(File dir, String name) {
        return name.endsWith("xmi");
      }});
    
    for(File xmiFile : xmiFiles){
      String name = xmiFile.getName();
      files.add(new File(textDir, name.substring(0, name.length()-4)));
    }
    return files;
  }
}
