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
package org.apache.ctakes.temporal.eval;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.apache.ctakes.temporal.ae.ContextualModalityAnnotator;
import org.apache.ctakes.temporal.ae.DocTimeRelAnnotator;
import org.apache.ctakes.typesystem.type.refsem.EventProperties;
import org.apache.ctakes.typesystem.type.textsem.EventMention;
import org.apache.ctakes.typesystem.type.textsem.TimeMention;
import org.apache.ctakes.typesystem.type.textspan.Segment;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Feature;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.pipeline.JCasIterator;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.FileUtils;
//import org.cleartk.ml.liblinear.LibLinearStringOutcomeDataWriter;
import org.cleartk.eval.AnnotationStatistics;
import org.cleartk.ml.jar.JarClassifierBuilder;
import org.cleartk.ml.libsvm.LibSvmStringOutcomeDataWriter;
import org.cleartk.util.ViewUriUtil;

import com.google.common.base.Function;
import com.google.common.collect.Maps;
import com.lexicalscope.jewel.cli.CliFactory;
import com.lexicalscope.jewel.cli.Option;

public class EvaluationOfEventProperties extends
Evaluation_ImplBase<Map<String, AnnotationStatistics<String>>> {
	static interface TempRelOptions extends Evaluation_ImplBase.Options{
		@Option
		public boolean getPrintFormattedRelations();

		@Option
		public boolean getBaseline();

		@Option
		public boolean getClosure();

		@Option
		public boolean getUseTmp();

		@Option
		public boolean getUseGoldAttributes();

		@Option
		public boolean getSkipTrain();
	}
	private static final String DOC_TIME_REL = "docTimeRel";
	private static final String CONTEXTUAL_MODALITY = "contextualModality";

	private static final List<String> PROPERTY_NAMES = Arrays.asList(DOC_TIME_REL, CONTEXTUAL_MODALITY);

	public static void main(String[] args) throws Exception {
		TempRelOptions options = CliFactory.parseArguments(TempRelOptions.class, args);
		List<Integer> patientSets = options.getPatients().getList();
		List<Integer> trainItems = getTrainItems(options);
		List<Integer> testItems = getTestItems(options);

		try{
			File workingDir = new File("target/eval/event-properties");
			if(!workingDir.exists()) workingDir.mkdirs();
			if(options.getUseTmp()){
				File tempModelDir = File.createTempFile("temporal", null, workingDir);
				tempModelDir.delete();
				tempModelDir.mkdir();
				workingDir = tempModelDir;
			}

			EvaluationOfEventProperties evaluation = new EvaluationOfEventProperties(
					workingDir,//new File("target/eval/event-properties"),
					options.getRawTextDirectory(),
					options.getXMLDirectory(),
					options.getXMLFormat(),
					options.getXMIDirectory());
			evaluation.skipTrain = options.getSkipTrain();
			if(evaluation.skipTrain && options.getTest()){
				evaluation.prepareXMIsFor(testItems);
			}else{
				evaluation.prepareXMIsFor(patientSets);
			}
			evaluation.logClassificationErrors(workingDir, "ctakes-event-property-errors");

			Map<String, AnnotationStatistics<String>> stats = evaluation.trainAndTest(trainItems, testItems);
			for (String name : PROPERTY_NAMES) {
				System.err.println("====================");
				System.err.println(name);
				System.err.println("--------------------");
				System.err.println(stats.get(name));
			}
			if(options.getUseTmp()){
				// won't work because it's not empty. should we be concerned with this or is it responsibility of 
				// person invoking the tmp flag?
				FileUtils.deleteRecursive(workingDir);
			}
		}catch(ResourceInitializationException e){
			System.err.println("Error with Initialization");
			e.printStackTrace();
		}
	}

	private Map<String, Logger> loggers = Maps.newHashMap();
	protected boolean skipTrain=false;

	public EvaluationOfEventProperties(
			File baseDirectory,
			File rawTextDirectory,
			File xmlDirectory,
			XMLFormat xmlFormat,
			File xmiDirectory) {
		super(baseDirectory, rawTextDirectory, xmlDirectory, xmlFormat, xmiDirectory, null);
		for (String name : PROPERTY_NAMES) {
			this.loggers.put(name, Logger.getLogger(String.format("%s.%s", this.getClass().getName(), name)));
		}
	}

	@Override
	protected void train(CollectionReader collectionReader, File directory) throws Exception {
		//	  if(this.baseline) return;
		if(this.skipTrain) return;
		AggregateBuilder aggregateBuilder = this.getPreprocessorAggregateBuilder();
		aggregateBuilder.add(CopyFromGold.getDescription(EventMention.class));
		aggregateBuilder.add(CopyFromGold.getDescription(TimeMention.class));
		aggregateBuilder.add(DocTimeRelAnnotator.createDataWriterDescription(
				LibSvmStringOutcomeDataWriter.class,
				new File(directory, DOC_TIME_REL)));
		aggregateBuilder.add(ContextualModalityAnnotator.createDataWriterDescription(
				LibSvmStringOutcomeDataWriter.class, 
				new File(directory, CONTEXTUAL_MODALITY)));
		SimplePipeline.runPipeline(collectionReader, aggregateBuilder.createAggregate());
		for(String propertyName : PROPERTY_NAMES){
			JarClassifierBuilder.trainAndPackage(new File(directory, propertyName), "-h","0","-c", "1000");
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	protected Map<String, AnnotationStatistics<String>> test(
			CollectionReader collectionReader,
			File directory) throws Exception {
		AggregateBuilder aggregateBuilder = this.getPreprocessorAggregateBuilder();
		aggregateBuilder.add(CopyFromGold.getDescription(EventMention.class));
		aggregateBuilder.add(CopyFromGold.getDescription(TimeMention.class));
		aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(ClearEventProperties.class));
		aggregateBuilder.add(DocTimeRelAnnotator.createAnnotatorDescription(new File(directory, DOC_TIME_REL)));
		aggregateBuilder.add(ContextualModalityAnnotator.createAnnotatorDescription(new File(directory, CONTEXTUAL_MODALITY)));

		Function<EventMention, ?> eventMentionToSpan = AnnotationStatistics.annotationToSpan();
		Map<String, Function<EventMention, String>> propertyGetters;
		propertyGetters = new HashMap<>();
		for (String name : PROPERTY_NAMES) {
			propertyGetters.put(name, getPropertyGetter(name));
		}

		Map<String, AnnotationStatistics<String>> statsMap = new HashMap<>();

		for(String propertyName : PROPERTY_NAMES){
			statsMap.put(propertyName, new AnnotationStatistics<String>());
		}

		for (Iterator<JCas> casIter = new JCasIterator(collectionReader, aggregateBuilder.createAggregate()); casIter.hasNext();){
			JCas jCas = casIter.next();
			JCas goldView = jCas.getView(GOLD_VIEW_NAME);
			JCas systemView = jCas.getView(CAS.NAME_DEFAULT_SOFA);
			String text = goldView.getDocumentText();
			for (Segment segment : JCasUtil.select(jCas, Segment.class)) {
				if (!THYMEData.SEGMENTS_TO_SKIP.contains(segment.getId())) {
					List<EventMention> goldEvents = selectExact(goldView, EventMention.class, segment);
					List<EventMention> systemEvents = selectExact(systemView, EventMention.class, segment);
					for (String name : PROPERTY_NAMES) {
						this.loggers.get(name).fine("Errors in : " + ViewUriUtil.getURI(jCas).toString());
						Function<EventMention, String> getProperty = propertyGetters.get(name);
						statsMap.get(name).add(
								goldEvents,
								systemEvents,
								eventMentionToSpan,
								getProperty);
						for (int i = 0; i < goldEvents.size(); ++i) {
							String goldOutcome = getProperty.apply(goldEvents.get(i));
							String systemOutcome = getProperty.apply(systemEvents.get(i));
							EventMention event = goldEvents.get(i);
							int begin = event.getBegin();
							int end = event.getEnd();
							int windowBegin = Math.max(0, begin - 50);
							int windowEnd = Math.min(text.length(), end + 50);
							if (!goldOutcome.equals(systemOutcome)) {
								this.loggers.get(name).fine(String.format(
										"%s was %s but should be %s, in  ...%s[!%s!:%d-%d]%s...",
										name,
										systemOutcome,
										goldOutcome,
										text.substring(windowBegin, begin).replaceAll("[\r\n]", " "),
										text.substring(begin, end),
										begin,
										end,
										text.substring(end, windowEnd).replaceAll("[\r\n]", " ")));
							}else{//if gold outcome equals system outcome
								this.loggers.get(name).fine(String.format(
										"%s was correctly labeled as %s, in  ...%s[!%s!:%d-%d]%s...",
										name,
										goldOutcome,
										text.substring(windowBegin, begin).replaceAll("[\r\n]", " "),
										text.substring(begin, end),
										begin,
										end,
										text.substring(end, windowEnd).replaceAll("[\r\n]", " ")));
							}
						}
					}
				}
			}
		}
		return statsMap;
	}

	public void logClassificationErrors(File outputDir, String outputFilePrefix) throws IOException {
		if (!outputDir.exists()) {
			outputDir.mkdirs();
		}
		for (String name : PROPERTY_NAMES) {
			Logger logger = this.loggers.get(name);
			logger.setLevel(Level.FINE);
			File outputFile = new File(outputDir, String.format("%s.%s.log", outputFilePrefix, name));
			FileHandler handler = new FileHandler(outputFile.getPath());
			handler.setFormatter(new Formatter() {
				@Override
				public String format(LogRecord record) {
					return record.getMessage() + '\n';
				}
			});
			logger.addHandler(handler);
		}
	}

	private static Function<EventMention, String> getPropertyGetter(final String propertyName) {
		return new Function<EventMention, String>() {
			@Override
			public String apply(EventMention eventMention) {
				EventProperties eventProperties = eventMention.getEvent().getProperties();
				Feature feature = eventProperties.getType().getFeatureByBaseName(propertyName);
				return eventProperties.getFeatureValueAsString(feature);
			}
		};
	}

	public static class ClearEventProperties extends org.apache.uima.fit.component.JCasAnnotator_ImplBase {
		@Override
		public void process(JCas jCas) throws AnalysisEngineProcessException {
			for (EventProperties eventProperties : JCasUtil.select(jCas, EventProperties.class)) {
				eventProperties.setAspect(null);
				eventProperties.setCategory(null);
				eventProperties.setContextualAspect(null);
				eventProperties.setContextualModality(null);
				eventProperties.setDegree(null);
				eventProperties.setDocTimeRel(null);
				eventProperties.setPermanence(null);
				eventProperties.setPolarity(0);
			}
		}

	}
}