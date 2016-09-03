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

import java.io.File;
//import java.io.IOException;
import java.util.List;
//import java.util.Map;

import org.apache.ctakes.temporal.ae.feature.ClosestVerbExtractor;
//import org.apache.ctakes.temporal.ae.feature.CoveredTextToValuesExtractor;
import org.apache.ctakes.temporal.ae.feature.DateAndMeasurementExtractor;
import org.apache.ctakes.temporal.ae.feature.EventPropertyExtractor;
import org.apache.ctakes.temporal.ae.feature.NearbyVerbTenseXExtractor;
import org.apache.ctakes.temporal.ae.feature.SectionHeaderExtractor;
import org.apache.ctakes.temporal.ae.feature.TimeXExtractor;
import org.apache.ctakes.temporal.ae.feature.UmlsSingleFeatureExtractor;
import org.apache.ctakes.typesystem.type.refsem.Event;
import org.apache.ctakes.typesystem.type.refsem.EventProperties;
//import org.apache.ctakes.temporal.ae.feature.duration.DurationExpectationFeatureExtractor;
import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.ctakes.typesystem.type.textsem.EventMention;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.ml.CleartkAnnotator;
import org.cleartk.ml.DataWriter;
import org.cleartk.ml.Feature;
import org.cleartk.ml.Instance;
import org.cleartk.ml.feature.extractor.CleartkExtractor;
import org.cleartk.ml.feature.extractor.CleartkExtractor.Covered;
import org.cleartk.ml.feature.extractor.CleartkExtractor.Following;
import org.cleartk.ml.feature.extractor.CleartkExtractor.Preceding;
import org.cleartk.ml.feature.extractor.CombinedExtractor1;
import org.cleartk.ml.feature.extractor.CoveredTextExtractor;
import org.cleartk.ml.feature.extractor.TypePathExtractor;
import org.cleartk.ml.jar.DefaultDataWriterFactory;
import org.cleartk.ml.jar.DirectoryDataWriterFactory;
import org.cleartk.ml.jar.GenericJarClassifierFactory;

//import com.google.common.base.Charsets;

public class DocTimeRelAnnotator extends CleartkAnnotator<String> {

  public static AnalysisEngineDescription createDataWriterDescription(
      Class<? extends DataWriter<String>> dataWriterClass,
      File outputDirectory) throws ResourceInitializationException {
    return AnalysisEngineFactory.createEngineDescription(
        DocTimeRelAnnotator.class,
        CleartkAnnotator.PARAM_IS_TRAINING,
        true,
        DefaultDataWriterFactory.PARAM_DATA_WRITER_CLASS_NAME,
        dataWriterClass,
        DirectoryDataWriterFactory.PARAM_OUTPUT_DIRECTORY,
        outputDirectory);
  }

  public static AnalysisEngineDescription createAnnotatorDescription(String modelPath)
	      throws ResourceInitializationException {
	    return AnalysisEngineFactory.createEngineDescription(
	        DocTimeRelAnnotator.class,
	        CleartkAnnotator.PARAM_IS_TRAINING,
	        false,
	        GenericJarClassifierFactory.PARAM_CLASSIFIER_JAR_PATH,
	        modelPath);
	  }
  
  /**
   * @deprecated use String path instead of File.
   * ClearTK will automatically Resolve the String to an InputStream.
   * This will allow resources to be read within from a jar as well as File.  
   */
  public static AnalysisEngineDescription createAnnotatorDescription(File modelDirectory)
      throws ResourceInitializationException {
    return AnalysisEngineFactory.createEngineDescription(
        DocTimeRelAnnotator.class,
        CleartkAnnotator.PARAM_IS_TRAINING,
        false,
        GenericJarClassifierFactory.PARAM_CLASSIFIER_JAR_PATH,
        new File(modelDirectory, "model.jar"));
  }

  private CleartkExtractor contextExtractor;
  private NearbyVerbTenseXExtractor verbTensePatternExtractor;
  private SectionHeaderExtractor sectionIDExtractor;
  private ClosestVerbExtractor closestVerbExtractor;
  private TimeXExtractor timeXExtractor;
  private EventPropertyExtractor genericExtractor;
  private DateAndMeasurementExtractor dateExtractor;
  private UmlsSingleFeatureExtractor umlsExtractor;
//  private CoveredTextToValuesExtractor disSemExtractor;
//  private DurationExpectationFeatureExtractor durationExtractor;
  
  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    CombinedExtractor1 baseExtractor = new CombinedExtractor1(
        new CoveredTextExtractor(),
        new TypePathExtractor(BaseToken.class, "partOfSpeech"));
    this.contextExtractor = new CleartkExtractor(
        BaseToken.class,
        baseExtractor,
        new Preceding(3),
        new Covered(),
        new Following(3));
    this.verbTensePatternExtractor = new NearbyVerbTenseXExtractor();
    this.sectionIDExtractor = new SectionHeaderExtractor();
    this.closestVerbExtractor = new ClosestVerbExtractor();
    this.timeXExtractor = new TimeXExtractor();
    this.genericExtractor = new EventPropertyExtractor();
    this.dateExtractor = new DateAndMeasurementExtractor();
    this.umlsExtractor = new UmlsSingleFeatureExtractor();
//    try {
//    	Map<String, double[]> word_disSem = CoveredTextToValuesExtractor.parseTextDoublesMap(new File("src/main/resources/embeddings.size25.txt"), Charsets.UTF_8);
//    	this.disSemExtractor = new CoveredTextToValuesExtractor("DisSemFeat", word_disSem);
//	} catch (IOException e) {
//		e.printStackTrace();
//	}
//    this.durationExtractor = new DurationExpectationFeatureExtractor();
  }

  @Override
  public void process(JCas jCas) throws AnalysisEngineProcessException {
    for (EventMention eventMention : JCasUtil.select(jCas, EventMention.class)) {
      List<Feature> features = this.contextExtractor.extract(jCas, eventMention);
      features.addAll(this.verbTensePatternExtractor.extract(jCas, eventMention));//add nearby verb POS pattern feature
      features.addAll(this.sectionIDExtractor.extract(jCas, eventMention)); //add section heading
      features.addAll(this.closestVerbExtractor.extract(jCas, eventMention)); //add closest verb
      features.addAll(this.timeXExtractor.extract(jCas, eventMention)); //add the closest time expression types
      features.addAll(this.genericExtractor.extract(jCas, eventMention)); //add the closest time expression types
      features.addAll(this.dateExtractor.extract(jCas, eventMention)); //add the closest NE type
      features.addAll(this.umlsExtractor.extract(jCas, eventMention)); //add umls features
      //        features.addAll(this.durationExtractor.extract(jCas, eventMention)); //add duration feature
      //        features.addAll(this.disSemExtractor.extract(jCas, eventMention)); //add distributional semantic features
      if (this.isTraining()) {
    	  if(eventMention.getEvent() != null){
    		  String outcome = eventMention.getEvent().getProperties().getDocTimeRel();
    		  this.dataWriter.write(new Instance<String>(outcome, features));
    	  }
      } else {
        String outcome = this.classifier.classify(features);
        if (eventMention.getEvent() == null) {
          Event event = new Event(jCas);
          eventMention.setEvent(event);
          EventProperties props = new EventProperties(jCas);
          event.setProperties(props);
        }
        eventMention.getEvent().getProperties().setDocTimeRel(outcome);
      }
    }
  }
}
