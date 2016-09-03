/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ctakes.temporal.ae;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.ctakes.clinicalpipeline.ClinicalPipelineFactory;
import org.apache.ctakes.clinicalpipeline.ClinicalPipelineFactory.CopyNPChunksToLookupWindowAnnotations;
import org.apache.ctakes.clinicalpipeline.ClinicalPipelineFactory.RemoveEnclosedLookupWindows;
import org.apache.ctakes.dependency.parser.ae.ClearNLPDependencyParserAE;
import org.apache.ctakes.dictionary.lookup.ae.UmlsDictionaryLookupAnnotator;
import org.apache.ctakes.temporal.ae.BackwardsTimeAnnotator;
import org.apache.ctakes.temporal.ae.EventAnnotator;
import org.apache.ctakes.typesystem.type.textsem.EventMention;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textsem.TimeMention;
import org.apache.log4j.Logger;
import org.apache.uima.UIMAException;
import org.apache.uima.jcas.JCas;
import org.cleartk.ml.CleartkAnnotator;
import org.cleartk.ml.jar.GenericJarClassifierFactory;
import org.junit.Test;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.fit.util.JCasUtil;

public class EventAnnotatorTest {

	// LOG4J logger based on class name
	private Logger LOGGER = Logger.getLogger(getClass().getName());

	@Test
	public void testPipeline() throws UIMAException, IOException {

		String note = "The patient is a 55-year-old man referred by Dr. Good for recently diagnosed colorectal cancer.  "
				+ "The patient was well till 6 months ago, when he started having a little blood with stool.";
		JCas jcas = JCasFactory.createJCas();
		jcas.setDocumentText(note);

		// Get the default pipeline
		AggregateBuilder builder = new AggregateBuilder();
		builder.add(ClinicalPipelineFactory.getTokenProcessingPipeline());
		builder.add(AnalysisEngineFactory
				.createEngineDescription(CopyNPChunksToLookupWindowAnnotations.class));
		builder.add(AnalysisEngineFactory
				.createEngineDescription(RemoveEnclosedLookupWindows.class));
		// Commented out the Dictionary lookup for the test
		// Uncomment and set -Dctakes.umlsuser and -Dctakes.umlspw env params if
		// needed
		// builder.add(UmlsDictionaryLookupAnnotator.createAnnotatorDescription());
		builder.add(ClearNLPDependencyParserAE.createAnnotatorDescription());

		// Add EventAnnotator
		builder.add(EventAnnotator
				.createAnnotatorDescription("/org/apache/ctakes/temporal/ae/eventannotator/model.jar"));

		SimplePipeline.runPipeline(jcas, builder.createAggregateDescription());

		Collection<EventMention> mentions = JCasUtil.select(jcas,
				EventMention.class);

		ArrayList<String> temp = new ArrayList<>();
		for (EventMention mention : mentions) {
			LOGGER.info("Event: " + mention.getCoveredText() );
			temp.add(mention.getCoveredText());
		}
		assertEquals(3, temp.size());
		assertTrue(temp.contains("old"));
		assertTrue(temp.contains("referred"));
		assertTrue(temp.contains("blood"));
	}
}
