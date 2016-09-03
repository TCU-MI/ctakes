package org.apache.ctakes.temporal.ae.feature.coreference;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.ctakes.relationextractor.ae.features.RelationFeaturesExtractor;
import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.cleartk.ml.Feature;
import org.apache.uima.fit.util.JCasUtil;

public class StringMatchingFeatureExtractor implements
		RelationFeaturesExtractor {

	@Override
	public List<Feature> extract(JCas jCas, IdentifiedAnnotation arg1,
			IdentifiedAnnotation arg2) throws AnalysisEngineProcessException {
		List<Feature> feats = new ArrayList<>();
		
		String s1 = arg1.getCoveredText();
		String s2 = arg2.getCoveredText();
		Set<String> words1 = contentWords(arg1);
		Set<String> words2 = contentWords(arg2);
		
		feats.add(new Feature("MATCH_EXACT",
				s1.equalsIgnoreCase(s2)));
		feats.add(new Feature("MATCH_START",
				startMatch(s1,s2)));
		feats.add(new Feature("MATCH_END",
				endMatch(s1,s2)));
		feats.add(new Feature("MATCH_SOON",
				soonMatch(s1,s2)));
		feats.add(new Feature("MATCH_OVERLAP",
				wordOverlap(words1, words2)));
		feats.add(new Feature("MATCH_SUBSTRING",
				wordSubstring(words1, words2)));
		return feats;
	}

	public static boolean startMatch (String a, String b) {
		int ia = a.indexOf(" ");
		int ib = b.indexOf(" ");
		String aa = a.substring(0, ia==-1?(a.length()>5?5:a.length()):ia);
		String bb = b.substring(0, ib==-1?(b.length()>5?5:b.length()):ib);
		return aa.equalsIgnoreCase(bb);
	}

	public static boolean endMatch (String a, String b) {
		int ia = a.lastIndexOf(" ");
		int ib = b.lastIndexOf(" ");
		String aa = a.substring(ia==-1?(a.length()>5?a.length()-5:0):ia);
		String bb = b.substring(ib==-1?(b.length()>5?b.length()-5:0):ib);
		return aa.equalsIgnoreCase(bb);
	}

	public static boolean soonMatch (String s1, String s2) {
		String sl1 = nonDetSubstr(s1.toLowerCase());
		String sl2 = nonDetSubstr(s2.toLowerCase());
		return sl1.equals(sl2);
	}

	public static String nonDetSubstr (String s) {
		if(s.startsWith("the ")) return s.substring(4);
		if(s.startsWith("a ")) return s.substring(2);
		if(s.startsWith("this ")) return s.substring(5);
		if(s.startsWith("that ")) return s.substring(5);
		if(s.startsWith("these ")) return s.substring(6);
		if(s.startsWith("those ")) return s.substring(6);
		return s;
	}

	public static boolean wordOverlap(Set<String> t1, Set<String> t2) {
		for (String s : t2){
			if (t1.contains(s)){
				return true;
			}
		}
		return false;
	}

	public static boolean wordSubstring(Set<String> t1, Set<String> t2){
		// TODO
		return false;
	}
	
	public static Set<String> contentWords(Annotation a1){
		Set<String> words = new HashSet<>();
		for(BaseToken tok : JCasUtil.selectCovered(BaseToken.class, a1)){
			words.add(tok.getCoveredText().toLowerCase());
		}
		return words;
	}
}
