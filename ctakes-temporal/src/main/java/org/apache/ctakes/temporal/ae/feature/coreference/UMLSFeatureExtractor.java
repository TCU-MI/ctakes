package org.apache.ctakes.temporal.ae.feature.coreference;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.apache.ctakes.dependency.parser.util.DependencyUtility;
import org.apache.ctakes.relationextractor.ae.features.RelationFeaturesExtractor;
import org.apache.ctakes.typesystem.type.refsem.UmlsConcept;
import org.apache.ctakes.typesystem.type.syntax.ConllDependencyNode;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textsem.Markable;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.cleartk.ml.Feature;
import org.apache.uima.fit.util.JCasUtil;

public class UMLSFeatureExtractor implements RelationFeaturesExtractor {

	@Override
	public List<Feature> extract(JCas jCas, IdentifiedAnnotation arg1,
			IdentifiedAnnotation arg2) throws AnalysisEngineProcessException {
		List<Feature> feats = new ArrayList<>();
		
		if(arg1 instanceof Markable && arg2 instanceof Markable){
		  // get the head of each markable
		  ConllDependencyNode head1 = DependencyUtility.getNominalHeadNode(jCas, arg1);
		  ConllDependencyNode head2 = DependencyUtility.getNominalHeadNode(jCas, arg2);
		  
		  if(head1 != null && head2 != null){
		    List<IdentifiedAnnotation> ents1 = JCasUtil.selectCovering(jCas, IdentifiedAnnotation.class, head1.getBegin(), head1.getEnd());
		    List<IdentifiedAnnotation> ents2 = JCasUtil.selectCovering(jCas, IdentifiedAnnotation.class, head2.getBegin(), head2.getEnd());

		    for(IdentifiedAnnotation ent1 : ents1){
		      for(IdentifiedAnnotation ent2 : ents2){
		        if(alias(ent1, ent2)){
		          feats.add(new Feature("UMLS_ALIAS", true));
		          break;
		        }
		      }
		    }
		  }
		}
		return feats;
	}

	public static boolean alias(IdentifiedAnnotation a1, IdentifiedAnnotation a2){  
	  if(a1 != null && a2 != null){
	    FSArray fsa = a1.getOntologyConceptArr();
	    if(fsa != null){
	      HashSet<String> cuis = new HashSet<>();
	      for(int i = 0; i < fsa.size(); i++){
	        if(fsa.get(i) instanceof UmlsConcept){
	          cuis.add(((UmlsConcept)fsa.get(i)).getCui());
	        }
	      }

	      fsa = a2.getOntologyConceptArr();
	      if(fsa != null){
	        for(int i = 0; i < fsa.size(); i++){
	          if(fsa.get(i) instanceof UmlsConcept){
	            if(cuis.contains(((UmlsConcept)fsa.get(i)).getCui())){
	              return true;
	            }
	          }
	        }
	      }
	    }
	  }
		return false;
	}
}
