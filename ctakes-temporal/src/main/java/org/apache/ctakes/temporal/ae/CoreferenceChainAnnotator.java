package org.apache.ctakes.temporal.ae;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.ctakes.typesystem.type.relation.CollectionTextRelation;
import org.apache.ctakes.typesystem.type.relation.CoreferenceRelation;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.EmptyFSList;
import org.apache.uima.jcas.cas.NonEmptyFSList;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.util.JCasUtil;

/*
 * Does not find coreference -- simply turns annotated pairs into chains of clustered mentions
 */
public class CoreferenceChainAnnotator extends JCasAnnotator_ImplBase {

  @Override
  public void process(JCas jCas) throws AnalysisEngineProcessException {
    Collection<CoreferenceRelation> pairs = JCasUtil.select(jCas, CoreferenceRelation.class);
    Map<Annotation,Set<Annotation>> chains = new HashMap<>();
    
    for(CoreferenceRelation pair : pairs){
      Annotation ante = pair.getArg1().getArgument();
      Annotation ana = pair.getArg2().getArgument();
      
      /* 3 cases:
       * 1) Only antecedent is in a chain -- add anaphor to that chain
       * 2) Only anaphor is in a chain -- add antecedent to that chain
       * 3) Both in different chains -- join the chains
       * 4) Both in same chain -- do nothing
       * 5) Neither in a chain -- create new chain
       */
      if(chains.containsKey(ante) && !chains.containsKey(ana)){
        // 1
        chains.get(ante).add(ana);
        chains.put(ana, chains.get(ante));
      }else if(chains.containsKey(ana) && !chains.containsKey(ante)){
        // 2
        chains.get(ana).add(ante);
        chains.put(ante, chains.get(ana));
      }else if(chains.containsKey(ante) && chains.containsKey(ana)){
        if(!chains.get(ante).equals(chains.get(ana))){
          // 3
          Set<Annotation> anteChain = chains.get(ante);
          Set<Annotation> anaChain = chains.get(ana);
          anteChain.addAll(anaChain);
          chains.put(ana, anteChain);
          // make all annotations in ana chain point to ante chain:
          for(Annotation markable : anaChain){
            chains.put(markable, anteChain);
          }
        }
        // else 4, which do nothing
      }else{
        // 5
        Set<Annotation> newChain = new HashSet<Annotation>();
        newChain.add(ante);
        newChain.add(ana);
        chains.put(ante, newChain);
        chains.put(ana, newChain);
      }
    }
    
    // convert java Sets into ordered UIMA lists.
    for(Set<Annotation> mentionSet : new HashSet<Set<Annotation>>(chains.values())){
      List<Annotation> sortedMentions = new ArrayList<>(mentionSet);
      Collections.sort(sortedMentions, new AnnotationComparator());
      CollectionTextRelation chain = new CollectionTextRelation(jCas);
      NonEmptyFSList list = new NonEmptyFSList(jCas);
      chain.setMembers(list);
      list.addToIndexes();
      for(int i = 0; i < sortedMentions.size(); i++){
        Annotation mention = sortedMentions.get(i);
        list.setHead(mention);
        if(i == (sortedMentions.size() - 1)){
          list.setTail(new EmptyFSList(jCas));
          list.getTail().addToIndexes();
        }else{
          list.setTail(new NonEmptyFSList(jCas));
          list = (NonEmptyFSList) list.getTail();
          list.addToIndexes();
        }
      }
      chain.addToIndexes();
    }
  }

  private class AnnotationComparator implements Comparator<Annotation> {

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
  
  public static AnalysisEngineDescription createAnnotatorDescription() throws ResourceInitializationException{
    return AnalysisEngineFactory.createEngineDescription(CoreferenceChainAnnotator.class);
  }
}
