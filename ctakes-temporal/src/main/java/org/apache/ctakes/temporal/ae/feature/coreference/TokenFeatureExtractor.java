package org.apache.ctakes.temporal.ae.feature.coreference;

import java.util.ArrayList;
import java.util.List;

import org.apache.ctakes.relationextractor.ae.features.RelationFeaturesExtractor;
import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.cleartk.ml.Feature;
import org.apache.uima.fit.util.JCasUtil;

public class TokenFeatureExtractor implements RelationFeaturesExtractor {

	@Override
	public List<Feature> extract(JCas jCas, IdentifiedAnnotation arg1,
			IdentifiedAnnotation arg2) throws AnalysisEngineProcessException {
		List<Feature> feats = new ArrayList<>();
		
		String s1 = arg1.getCoveredText().toLowerCase();
		String s2 = arg2.getCoveredText().toLowerCase();
		
		boolean dem1 = isDemonstrative(s1);
		boolean dem2 = isDemonstrative(s2);
		
		feats.add(new Feature("TOKEN_DEM1", dem1));
		feats.add(new Feature("TOKEN_DEM2", dem2));
		feats.add(new Feature("TOKEN_DEF1", isDefinite(s1)));
		feats.add(new Feature("TOKEN_DEF2", isDefinite(s2)));
		feats.add(new Feature("TOKEN_NUMAGREE",
				numberSingular(arg1) == numberSingular(arg2)));
		
		String gen1 = getGender(s1);
		String gen2 = getGender(s2);
		feats.add(new Feature("TOKEN_GEN1", gen1));
		feats.add(new Feature("TOKEN_GEN2", gen2));
		feats.add(new Feature("TOKEN_GENAGREE", gen1.equals(gen2)));
		
		String p1 = getPerson(s1);
		String p2 = getPerson(s2);
		feats.add(new Feature("TOKEN_PERSON1", p1));
		feats.add(new Feature("TOKEN_PERSON2", p2));
		feats.add(new Feature("TOKEN_PERSONAGREE", p1.equals(p2)));
		return feats;
	}
	
	public static boolean isDemonstrative (String s) {
		if (s.startsWith("this") ||
				s.startsWith("that") ||
				s.startsWith("these") ||
				s.startsWith("those")){
				return true;
		}
		return false;
	}
	
	public static boolean isDefinite (String s) {
		return s.startsWith("the ");
	}

	// FYI - old code used treebanknode types and found head using head rules filled in by the parser
	// not sure if there is an appreciable difference...
	public static boolean numberSingular(IdentifiedAnnotation arg){
		List<BaseToken> tokens = new ArrayList<>(JCasUtil.selectCovered(BaseToken.class, arg));
		for (int i = tokens.size()-1; i >=0; i--){
			BaseToken t = tokens.get(i);
			String pos = t.getPartOfSpeech();
			if ("NN".equals(pos) || "NNP".equals(pos)){
				return true;
			}else if ("NNS".equals(pos) || "NNPS".equals(pos)){
				return false;
			}else if(t.getCoveredText().toLowerCase().equals("we")){
			  return true;
			}
		}
		return true;
	}
	
	public static String getGender(String s1){
	  if(s1.equals("he") || s1.equals("his") || s1.startsWith("mr.")) return "MALE";
	  else if(s1.equals("she") || s1.equals("her") || s1.startsWith("mrs.") || s1.startsWith("ms.")) return "FEMALE";
	  else return "NEUTER";
	}
	
	public static String getPerson(String s1){
	  if(s1.equals("i")) return "FIRST";
	  else if(s1.equals("he") || s1.equals("she") || s1.equals("his") || s1.equals("her") || s1.equals("hers")){
	    return "THIRD";
	  }else if(s1.equals("you") || s1.equals("your")) return "SECOND";
	  else if(s1.equals("we")) return "FIRST_PLURAL";
	  else return "NONE";
	}
	
	public static boolean getAnimate(String s1){
	  if(s1.equals("i")) return true;
	  return false;
	}
}
