package org.apache.ctakes.temporal.ae.feature;

import java.util.ArrayList;
import java.util.List;

import org.apache.ctakes.relationextractor.ae.features.TokenFeaturesExtractor;
import org.apache.ctakes.typesystem.type.syntax.WordToken;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.cleartk.ml.Feature;
import org.apache.uima.fit.util.JCasUtil;

/**
 * Extract the overlapping head words of two arguments. Head words: the NNs of NP + the VBs of VP
 * @author CH151862
 *
 */
public class OverlappedHeadFeaturesExtractor extends TokenFeaturesExtractor {

	@Override
	public List<Feature> extract(JCas jCas, IdentifiedAnnotation mention1, IdentifiedAnnotation mention2)
			throws AnalysisEngineProcessException {
		List<Feature> features = new ArrayList<>();
		Annotation arg1 = mention1;
		Annotation arg2 = mention2;
		
		String featName = "overlappingHeadTerms";

		//iterate through the tokens of two arguments
		List<WordToken> currentTokens = JCasUtil.selectCovered(jCas, WordToken.class, arg1);
		List<WordToken> nextTokens = JCasUtil.selectCovered(jCas, WordToken.class, arg2);
		
		int headSize1 = 0;
		int headSize2 = 0;
		int headSize  = 0;
		int longHeadSize = 0;
		int matches = 0;
		for(WordToken t1: currentTokens){
			String t1_pos = t1.getPartOfSpeech();
			if(t1_pos.startsWith("NN")||t1_pos.startsWith("VB")){
				headSize1 ++;
				for(WordToken t2: nextTokens){
					String t2_pos = t2.getPartOfSpeech();
					if(t2_pos.startsWith("NN")||t2_pos.startsWith("VB")){
						headSize2 ++;
						String t1str = t1.getCanonicalForm();
						String t2str = t2.getCanonicalForm();
						if(t1str.equals(t2str)){
							features.add(new Feature(featName+"_CanoticalForm", t1str));
							features.add(new Feature(featName+"_length", t1str.length()));
							features.add(new Feature(featName+"_POS", t1_pos));
							matches++;
						}
					}
				}
			}
		}
		if(matches > 0){
			headSize = Math.min(headSize1, headSize2);
			longHeadSize = Math.max(headSize1, headSize2);
			
			//feature of counting times of matches
			features.add(new Feature(featName+"_count", matches));
			
			//ratio of the count of matches to the shorter length of tokens between the two arguments
			float matchShortRatio = (float)matches/headSize;
			features.add(new Feature(featName+"_shortRatio", matchShortRatio));
			
			//ratio of the count of matches to the longer length of tokens between the two arguments
			float matchLongRatio  = (float)matches/longHeadSize;
			features.add(new Feature(featName+"_longRatio", matchLongRatio));
		}
		
		return features;
	}

}
