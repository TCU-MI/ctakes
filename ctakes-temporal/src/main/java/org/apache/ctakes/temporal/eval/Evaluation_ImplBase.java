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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.ctakes.chunker.ae.Chunker;
import org.apache.ctakes.chunker.ae.DefaultChunkCreator;
import org.apache.ctakes.chunker.ae.adjuster.ChunkAdjuster;
import org.apache.ctakes.constituency.parser.ae.ConstituencyParser;
import org.apache.ctakes.contexttokenizer.ae.ContextDependentTokenizerAnnotator;
import org.apache.ctakes.core.ae.OverlapAnnotator;
import org.apache.ctakes.core.ae.SentenceDetector;
import org.apache.ctakes.core.ae.SimpleSegmentAnnotator;
import org.apache.ctakes.core.ae.TokenizerAnnotatorPTB;
import org.apache.ctakes.core.resource.FileLocator;
import org.apache.ctakes.core.resource.FileResourceImpl;
import org.apache.ctakes.core.resource.JdbcConnectionResourceImpl;
import org.apache.ctakes.core.resource.LuceneIndexReaderResourceImpl;
import org.apache.ctakes.dependency.parser.ae.ClearNLPDependencyParserAE;
import org.apache.ctakes.dependency.parser.ae.ClearNLPSemanticRoleLabelerAE;
import org.apache.ctakes.dictionary.lookup.ae.UmlsDictionaryLookupAnnotator;
import org.apache.ctakes.lvg.ae.LvgAnnotator;
import org.apache.ctakes.lvg.resource.LvgCmdApiResourceImpl;
import org.apache.ctakes.postagger.POSTagger;
import org.apache.ctakes.temporal.ae.I2B2TemporalXMLReader;
import org.apache.ctakes.temporal.ae.THYMEAnaforaXMLReader;
import org.apache.ctakes.temporal.ae.THYMEKnowtatorXMLReader;
import org.apache.ctakes.temporal.ae.THYMETreebankReader;
import org.apache.ctakes.typesystem.type.relation.TemporalTextRelation;
import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.ctakes.typesystem.type.syntax.Chunk;
import org.apache.ctakes.typesystem.type.syntax.TerminalTreebankNode;
import org.apache.ctakes.typesystem.type.syntax.TreebankNode;
import org.apache.ctakes.typesystem.type.textsem.EntityMention;
import org.apache.ctakes.typesystem.type.textsem.EventMention;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textsem.TimeMention;
import org.apache.ctakes.typesystem.type.textspan.LookupWindowAnnotation;
import org.apache.ctakes.typesystem.type.textspan.Segment;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.log4j.Logger;
import org.apache.uima.UIMAException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.impl.XmiCasDeserializer;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.CasCopier;
import org.apache.uima.util.XMLSerializer;
import org.cleartk.util.ViewUriUtil;
import org.cleartk.util.ae.UriToDocumentTextAnnotator;
import org.cleartk.util.cr.UriCollectionReader;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.component.ViewCreatorAnnotator;
import org.apache.uima.fit.component.ViewTextCopierAnnotator;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.ExternalResourceFactory;
import org.apache.uima.fit.factory.TypePrioritiesFactory;
import org.apache.uima.fit.factory.TypeSystemDescriptionFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.fit.util.JCasUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.google.common.collect.Lists;
import com.lexicalscope.jewel.cli.Option;

public abstract class Evaluation_ImplBase<STATISTICS_TYPE> extends
org.cleartk.eval.Evaluation_ImplBase<Integer, STATISTICS_TYPE> {

	private static Logger LOGGER = Logger.getLogger(Evaluation_ImplBase.class);

	public static final String GOLD_VIEW_NAME = "GoldView";

	enum XMLFormat { Knowtator, Anafora, I2B2 }

	static interface Options {

		@Option(longName = "text", defaultToNull = true)
		public File getRawTextDirectory();

		@Option(longName = "xml")
		public File getXMLDirectory();

		@Option(longName = "format", defaultValue="Anafora")
		public XMLFormat getXMLFormat();

		@Option(longName = "xmi")
		public File getXMIDirectory();

		@Option(longName = "patients")
		public CommandLine.IntegerRanges getPatients();

		@Option(longName = "train-remainders", defaultValue = "0-2")
		public CommandLine.IntegerRanges getTrainRemainders();

		@Option(longName = "dev-remainders", defaultValue = "3")
		public CommandLine.IntegerRanges getDevRemainders();

		@Option(longName = "test-remainders", defaultValue = "4-5")
		public CommandLine.IntegerRanges getTestRemainders();

		@Option(longName = "treebank", defaultToNull=true)
		public File getTreebankDirectory();

		@Option(longName = "coreference", defaultToNull=true)
		public File getCoreferenceDirectory();

		@Option
		public boolean getUseGoldTrees();

		@Option
		public boolean getGrid();

		@Option
		public boolean getPrintErrors();

		@Option
		public boolean getPrintOverlappingSpans();

		@Option
		public boolean getTest();

		@Option(longName = "kernelParams", defaultToNull=true)
		public String getKernelParams();

		@Option(defaultToNull=true)
		public String getI2B2Output();
	}

    public static List<Integer> getTrainItems(Options options) {
        List<Integer> patientSets = options.getPatients().getList();
        List<Integer> trainItems = THYMEData.getPatientSets(patientSets, options.getTrainRemainders().getList());
        if (options.getTest()) {
            trainItems.addAll(THYMEData.getPatientSets(patientSets, options.getDevRemainders().getList()));
        }
        return trainItems;
    }

    public static List<Integer> getTestItems(Options options) {
        List<Integer> patientSets = options.getPatients().getList();
        List<Integer> testItems;
        if (options.getTest()) {
            testItems = THYMEData.getPatientSets(patientSets, options.getTestRemainders().getList());
        } else {
            testItems = THYMEData.getPatientSets(patientSets, options.getDevRemainders().getList());
        }
        return testItems;
    }

    protected File rawTextDirectory;

	protected File xmlDirectory;

	protected XMLFormat xmlFormat;

	protected File xmiDirectory;

	private boolean xmiExists;

	protected File treebankDirectory;

	protected File coreferenceDirectory;

	protected boolean printErrors = false;

	protected boolean printOverlapping = false;

	protected String i2b2Output = null;

	protected String[] kernelParams;

	public Evaluation_ImplBase(
			File baseDirectory,
			File rawTextDirectory,
			File xmlDirectory,
			XMLFormat xmlFormat,
			File xmiDirectory,
			File treebankDirectory,
			File coreferenceDirectory) {
		super(baseDirectory);
		this.rawTextDirectory = rawTextDirectory;
		this.xmlDirectory = xmlDirectory;
		this.xmlFormat = xmlFormat;
		this.xmiDirectory = xmiDirectory;
		this.xmiExists = this.xmiDirectory.exists() && this.xmiDirectory.listFiles().length > 0;
		this.treebankDirectory = treebankDirectory;
		this.coreferenceDirectory = coreferenceDirectory;
	}

	public Evaluation_ImplBase(
			File baseDirectory,
			File rawTextDirectory,
			File xmlDirectory,
			XMLFormat xmlFormat,
			File xmiDirectory,
			File treebankDirectory) {
		this(baseDirectory, rawTextDirectory, xmlDirectory, xmlFormat,
				xmiDirectory, treebankDirectory, null);
	}

	public void setI2B2Output(String outDir){
		i2b2Output = outDir;
	}

	public void prepareXMIsFor(List<Integer> patientSets) throws Exception {
		boolean needsXMIs = false;
		for (File textFile : this.getFilesFor(patientSets)) {
			if (!getXMIFile(this.xmiDirectory, textFile).exists()) {
				needsXMIs = true;
				break;
			}
		}
		if (needsXMIs) {
			CollectionReader reader = this.getCollectionReader(patientSets);
			AnalysisEngine engine = this.getXMIWritingPreprocessorAggregateBuilder().createAggregate();
			SimplePipeline.runPipeline(reader, engine);
		}
		this.xmiExists = true;
	}

	private List<File> getFilesFor(List<Integer> patientSets) throws FileNotFoundException {
		List<File> files = new ArrayList<>();
		if (this.rawTextDirectory == null
				&& this.xmlFormat == XMLFormat.Anafora) {
			for (File dir : this.xmlDirectory.listFiles()) {
				Set<String> ids = new HashSet<>();
				for (Integer set : patientSets) {
					ids.add(String.format("ID%03d", set));
				}
				if (dir.isDirectory()) {
					if (ids.contains(dir.getName().substring(0, 5))) {
						File file = new File(dir, dir.getName());
						if (file.exists()) {
							files.add(file);
						} else {
							LOGGER.warn("Missing note: " + file);
						}
					} else {
						LOGGER.info("Skipping note: " + dir);
					}
				}
			}
		} else if(this.xmlFormat == XMLFormat.I2B2) {
			File trainDir = new File(this.xmlDirectory, "training");
			File testDir = new File(this.xmlDirectory, "test");
			for (Integer pt : patientSets){
				File xmlTrain = new File(trainDir, pt+".xml");
				File train = new File(trainDir, pt+".xml.txt");
				if(train.exists()){
					if(xmlTrain.exists()){
						files.add(train);
					}else{
						System.err.println("Text file in training has no corresponding xml -- skipping: " + train);
					}
				}
				File xmlTest = new File(testDir, pt+".xml");
				File test = new File(testDir, pt+".xml.txt");
				if(xmlTest.exists()){
					if(test.exists()){
						files.add(test);
					}else{
						throw new FileNotFoundException("Could not find the test text file -- for cTAKES usage you must copy the text files into the xml directory for the test set.");
					}
				}
				assert !(train.exists() && test.exists());
			}
		}	else  {
			for (Integer set : patientSets) {
				final int setNum = set;
				for (File file : rawTextDirectory.listFiles(new FilenameFilter(){
					@Override
					public boolean accept(File dir, String name) {
						return name.contains(String.format("ID%03d", setNum));
					}})) {
					// skip hidden files like .svn
					if (!file.isHidden()) {
						if(xmlFormat == XMLFormat.Knowtator){
							files.add(file);
						}else{
							// look for equivalent in xml directory:
							File xmlFile = new File(xmlDirectory, file.getName());
							if(xmlFile.exists()){
								if(coreferenceDirectory != null){
									// verify that coref version of xml exists
									File corefFile = new File(coreferenceDirectory, file.getName()+".Coreference.gold.completed.xml");
									if(corefFile.exists() && xmlFile.exists()){
										files.add(file);
									}else{
										System.err.println("Missing coref patient file : " + corefFile);
									}
								}else{
									files.add(file);
								}
							}else{
								System.err.println("Missing patient file : " + xmlFile);
							}
						}
					} 
				}
			}
		}
		return files;
	}

	@Override
	protected CollectionReader getCollectionReader(List<Integer> patientSets) throws Exception {
		return UriCollectionReader.getCollectionReaderFromFiles(this.getFilesFor(patientSets));
	}

	protected AggregateBuilder getPreprocessorAggregateBuilder() throws Exception {
		return this.xmiExists
				? this.getXMIReadingPreprocessorAggregateBuilder()
						: this.getXMIWritingPreprocessorAggregateBuilder();
	}

	protected AggregateBuilder getXMIReadingPreprocessorAggregateBuilder() throws UIMAException {
		AggregateBuilder aggregateBuilder = new AggregateBuilder();
		aggregateBuilder.add(UriToDocumentTextAnnotator.getDescription());
		aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(
				XMIReader.class,
				XMIReader.PARAM_XMI_DIRECTORY,
				this.xmiDirectory));
		return aggregateBuilder;
	}

	protected AggregateBuilder getXMIWritingPreprocessorAggregateBuilder()
			throws Exception {
		AggregateBuilder aggregateBuilder = new AggregateBuilder();
		aggregateBuilder.add(UriToDocumentTextAnnotator.getDescription());

		// read manual annotations into gold view
		aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(
				ViewCreatorAnnotator.class,
				ViewCreatorAnnotator.PARAM_VIEW_NAME,
				GOLD_VIEW_NAME));
		aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(
				ViewTextCopierAnnotator.class,
				ViewTextCopierAnnotator.PARAM_SOURCE_VIEW_NAME,
				CAS.NAME_DEFAULT_SOFA,
				ViewTextCopierAnnotator.PARAM_DESTINATION_VIEW_NAME,
				GOLD_VIEW_NAME));
		switch (this.xmlFormat) {
		case Anafora:
			aggregateBuilder.add(
					THYMEAnaforaXMLReader.getDescription(this.xmlDirectory),
					CAS.NAME_DEFAULT_SOFA,
					GOLD_VIEW_NAME);
			break;
		case Knowtator:
			aggregateBuilder.add(
					THYMEKnowtatorXMLReader.getDescription(this.xmlDirectory),
					CAS.NAME_DEFAULT_SOFA,
					GOLD_VIEW_NAME);
			break;
		case I2B2:
			aggregateBuilder.add(
					I2B2TemporalXMLReader.getDescription(this.xmlDirectory),
					CAS.NAME_DEFAULT_SOFA,
					GOLD_VIEW_NAME);
			break;
		}

		if(this.coreferenceDirectory != null){
			aggregateBuilder.add(
					THYMEAnaforaXMLReader.getDescription(this.coreferenceDirectory),
					CAS.NAME_DEFAULT_SOFA,
					GOLD_VIEW_NAME);
		}

		// identify segments
		if(this.xmlFormat == XMLFormat.I2B2){
			aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(SimpleSegmentAnnotator.class));
		}else{
			aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(SegmentsFromBracketedSectionTagsAnnotator.class));
		}
		// identify sentences
		aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(
				SentenceDetector.class,
				SentenceDetector.SD_MODEL_FILE_PARAM,
				"org/apache/ctakes/core/sentdetect/sd-med-model.zip"));
		// identify tokens
		aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(TokenizerAnnotatorPTB.class));
		// merge some tokens
		aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(ContextDependentTokenizerAnnotator.class));

		// identify part-of-speech tags
		aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(
				POSTagger.class,
				TypeSystemDescriptionFactory.createTypeSystemDescription(),
				TypePrioritiesFactory.createTypePriorities(Segment.class, Sentence.class, BaseToken.class),
				POSTagger.POS_MODEL_FILE_PARAM,
				"org/apache/ctakes/postagger/models/mayo-pos.zip"));

		// identify chunks
		aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(
				Chunker.class,
				Chunker.CHUNKER_MODEL_FILE_PARAM,
				FileLocator.locateFile("org/apache/ctakes/chunker/models/chunker-model.zip"),
				Chunker.CHUNKER_CREATOR_CLASS_PARAM,
				DefaultChunkCreator.class));

		// identify UMLS named entities

		// adjust NP in NP NP to span both
		aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(
				ChunkAdjuster.class,
				ChunkAdjuster.PARAM_CHUNK_PATTERN,
				new String[] { "NP", "NP" },
				ChunkAdjuster.PARAM_EXTEND_TO_INCLUDE_TOKEN,
				1));
		// adjust NP in NP PP NP to span all three
		aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(
				ChunkAdjuster.class,
				ChunkAdjuster.PARAM_CHUNK_PATTERN,
				new String[] { "NP", "PP", "NP" },
				ChunkAdjuster.PARAM_EXTEND_TO_INCLUDE_TOKEN,
				2));
		// add lookup windows for each NP
		aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(CopyNPChunksToLookupWindowAnnotations.class));
		// maximize lookup windows
		aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(
				OverlapAnnotator.class,
				"A_ObjectClass",
				LookupWindowAnnotation.class,
				"B_ObjectClass",
				LookupWindowAnnotation.class,
				"OverlapType",
				"A_ENV_B",
				"ActionType",
				"DELETE",
				"DeleteAction",
				new String[] { "selector=B" }));
		// add UMLS on top of lookup windows
		aggregateBuilder.add(
				UmlsDictionaryLookupAnnotator.createAnnotatorDescription()
				);

		/*
    // add lvg annotator
    String[] XeroxTreebankMap = {
        "adj|JJ",
        "adv|RB",
        "aux|AUX",
        "compl|CS",
        "conj|CC",
        "det|DET",
        "modal|MD",
        "noun|NN",
        "prep|IN",
        "pron|PRP",
        "verb|VB" };
    String[] ExclusionSet = {
        "and",
        "And",
        "by",
        "By",
        "for",
        "For",
        "in",
        "In",
        "of",
        "Of",
        "on",
        "On",
        "the",
        "The",
        "to",
        "To",
        "with",
        "With" };
    AnalysisEngineDescription lvgAnnotator = AnalysisEngineFactory.createEngineDescription(
        LvgAnnotator.class,
        "UseSegments",
        false,
        "SegmentsToSkip",
        new String[0],
        "UseCmdCache",
        false,
        "CmdCacheFileLocation",
        "/org/apache/ctakes/lvg/2005_norm.voc",
        "CmdCacheFrequencyCutoff",
        20,
        "ExclusionSet",
        ExclusionSet,
        "XeroxTreebankMap",
        XeroxTreebankMap,
        "LemmaCacheFileLocation",
        "/org/apache/ctakes/lvg/2005_lemma.voc",
        "UseLemmaCache",
        false,
        "LemmaCacheFrequencyCutoff",
        20,
        "PostLemmas",
        false,
        "LvgCmdApi",
        ExternalResourceFactory.createExternalResourceDescription(
            LvgCmdApiResourceImpl.class,
            new File(LvgCmdApiResourceImpl.class.getResource(
                "/org/apache/ctakes/lvg/data/config/lvg.properties").toURI())));
    aggregateBuilder.add(lvgAnnotator);
		 */
		aggregateBuilder.add(LvgAnnotator.createAnnotatorDescription());

		// add dependency parser
		aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(ClearNLPDependencyParserAE.class));

		// add semantic role labeler
		aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(ClearNLPSemanticRoleLabelerAE.class));

		// add gold standard parses to gold view, and adjust gold view to correct a few annotation mis-steps
		if(this.treebankDirectory != null){
			aggregateBuilder.add(THYMETreebankReader.getDescription(this.treebankDirectory));
			aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(TimexAnnotationCorrector.class));
		}else{
			// add ctakes constituency parses to system view
			aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(ConstituencyParser.class,
					ConstituencyParser.PARAM_MODEL_FILENAME,
					"org/apache/ctakes/constituency/parser/models/thyme.bin"));
			//          "org/apache/ctakes/constituency/parser/models/sharp-3.1.bin"));
			//            "org/apache/ctakes/constituency/parser/models/thymeNotempeval.bin"));
			//      aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(BerkeleyParserWrapper.class,
			//          BerkeleyParserWrapper.PARAM_MODEL_FILENAME,
			//          
			//        "org/apache/ctakes/constituency/parser/models/thyme.gcg.4sm.bin"));
			//          "org/apache/ctakes/constituency/parser/models/thyme.4sm.bin"));
		}
		// write out the CAS after all the above annotations
		aggregateBuilder.add(AnalysisEngineFactory.createEngineDescription(
				XMIWriter.class,
				XMIWriter.PARAM_XMI_DIRECTORY,
				this.xmiDirectory));

		return aggregateBuilder;
	}

	public static <T extends Annotation> List<T> selectExact(JCas jCas, Class<T> annotationClass, Segment segment) {
		List<T> annotations = Lists.newArrayList();
		for (T annotation : JCasUtil.selectCovered(jCas, annotationClass, segment)) {
			if (annotation.getClass().equals(annotationClass)) {
				annotations.add(annotation);
			}
		}
		return annotations;
	}

	public static class CopyNPChunksToLookupWindowAnnotations extends JCasAnnotator_ImplBase {

		@Override
		public void process(JCas jCas) throws AnalysisEngineProcessException {
			for (Chunk chunk : JCasUtil.select(jCas, Chunk.class)) {
				if (chunk.getChunkType().equals("NP")) {
					new LookupWindowAnnotation(jCas, chunk.getBegin(), chunk.getEnd()).addToIndexes();
				}
			}
		}
	}

	public static class RemoveEnclosedLookupWindows extends JCasAnnotator_ImplBase {

		@Override
		public void process(JCas jCas) throws AnalysisEngineProcessException {
			List<LookupWindowAnnotation> lws = new ArrayList<>(JCasUtil.select(jCas, LookupWindowAnnotation.class));
			// we'll navigate backwards so that as we delete things we shorten the list from the back
			for(int i = lws.size()-2; i >= 0; i--){
				LookupWindowAnnotation lw1 = lws.get(i);
				LookupWindowAnnotation lw2 = lws.get(i+1);
				if(lw1.getBegin() <= lw2.getBegin() && lw1.getEnd() >= lw2.getEnd()){
					/// lw1 envelops or encloses lw2
					lws.remove(i+1);
					lw2.removeFromIndexes();
				}
			}

		}

	}

	public static class EntityMentionRemover extends JCasAnnotator_ImplBase {

		@Override
		public void process(JCas jCas) throws AnalysisEngineProcessException {
			for (EntityMention mention : Lists.newArrayList(JCasUtil.select(jCas, EntityMention.class))) {
				mention.removeFromIndexes();
			}
		}
	}

	public static class EventMentionRemover extends JCasAnnotator_ImplBase {

		@Override
		public void process(JCas jCas) throws AnalysisEngineProcessException {
			for (EventMention mention : Lists.newArrayList(JCasUtil.select(jCas, EventMention.class))) {
				mention.removeFromIndexes();
			}
		}
	}

	// replace this with SimpleSegmentWithTagsAnnotator if that code ever gets fixed
	public static class SegmentsFromBracketedSectionTagsAnnotator extends JCasAnnotator_ImplBase {
		private static Pattern SECTION_PATTERN = Pattern.compile(
				"(\\[start section id=\"?(.*?)\"?\\]).*?(\\[end section id=\"?(.*?)\"?\\])",
				Pattern.DOTALL);

		@Override
		public void process(JCas jCas) throws AnalysisEngineProcessException {
			Matcher matcher = SECTION_PATTERN.matcher(jCas.getDocumentText());
			while (matcher.find()) {
				Segment segment = new Segment(jCas);
				segment.setBegin(matcher.start() + matcher.group(1).length());
				segment.setEnd(matcher.end() - matcher.group(3).length());
				segment.setId(matcher.group(2));
				segment.addToIndexes();
			}
		}
	}

	static File getXMIFile(File xmiDirectory, File textFile) {
		return new File(xmiDirectory, textFile.getName() + ".xmi");
	}

	static File getXMIFile(File xmiDirectory, JCas jCas) throws AnalysisEngineProcessException {
		return getXMIFile(xmiDirectory, new File(ViewUriUtil.getURI(jCas).getPath()));
	}

	public static class XMIWriter extends JCasAnnotator_ImplBase {

		public static final String PARAM_XMI_DIRECTORY = "XMIDirectory";

		@ConfigurationParameter(name = PARAM_XMI_DIRECTORY, mandatory = true)
		private File xmiDirectory;

		@Override
		public void initialize(UimaContext context) throws ResourceInitializationException {
			super.initialize(context);
			if (!this.xmiDirectory.exists()) {
				this.xmiDirectory.mkdirs();
			}
		}

		@Override
		public void process(JCas jCas) throws AnalysisEngineProcessException {
			File xmiFile = getXMIFile(this.xmiDirectory, jCas);
			try {
				FileOutputStream outputStream = new FileOutputStream(xmiFile);
				try {
					XmiCasSerializer serializer = new XmiCasSerializer(jCas.getTypeSystem());
					ContentHandler handler = new XMLSerializer(outputStream, false).getContentHandler();
					serializer.serialize(jCas.getCas(), handler);
				} finally {
					outputStream.close();
				}
			} catch (SAXException e) {
				throw new AnalysisEngineProcessException(e);
			} catch (IOException e) {
				throw new AnalysisEngineProcessException(e);
			}
		}
	}

	public static class XMIReader extends JCasAnnotator_ImplBase {

		public static final String PARAM_XMI_DIRECTORY = "XMIDirectory";

		@ConfigurationParameter(name = PARAM_XMI_DIRECTORY, mandatory = true)
		private File xmiDirectory;

		@Override
		public void process(JCas jCas) throws AnalysisEngineProcessException {
			File xmiFile = getXMIFile(this.xmiDirectory, jCas);
			try {
				FileInputStream inputStream = new FileInputStream(xmiFile);
				try {
					XmiCasDeserializer.deserialize(inputStream, jCas.getCas());
				} finally {
					inputStream.close();
				}
			} catch (SAXException e) {
				throw new AnalysisEngineProcessException(e);
			} catch (IOException e) {
				throw new AnalysisEngineProcessException(e);
			}
		}
	}

	public static class TimexAnnotationCorrector extends JCasAnnotator_ImplBase {
		@Override
		public void process(JCas jCas) throws AnalysisEngineProcessException {
			JCas goldView, systemView;
			try {
				goldView = jCas.getView(GOLD_VIEW_NAME);
				systemView = jCas.getView(CAS.NAME_DEFAULT_SOFA);
			} catch (CASException e) {
				e.printStackTrace();
				throw new AnalysisEngineProcessException();
			}
			for(TimeMention mention : JCasUtil.select(goldView, TimeMention.class)){
				// for each time expression, get the treebank node with the same span.
				List<TreebankNode> nodes = JCasUtil.selectCovered(systemView, TreebankNode.class, mention);
				TreebankNode sameSpanNode = null;
				for(TreebankNode node : nodes){
					if(node.getBegin() == mention.getBegin() && node.getEnd() == mention.getEnd()){
						sameSpanNode = node;
						break;
					}
				}
				if(sameSpanNode != null){
					// look at node at the position of the timex3.
					if(sameSpanNode.getNodeType().equals("PP")){
						// if it is a PP it should be moved down to the NP
						int numChildren = sameSpanNode.getChildren().size();
						if(numChildren == 2 && sameSpanNode.getChildren(0).getNodeType().equals("IN") && sameSpanNode.getChildren(1).getNodeType().equals("NP")){
							// move the time span to this node:
							TreebankNode mentionNode = sameSpanNode.getChildren(numChildren-1);
							mention.setBegin(mentionNode.getBegin());
							mention.setEnd(mentionNode.getEnd());
						}
					}
				}else{
					// if there is no matching tree span, see if the DT to the left would help.
					// now adjust for missing DT to the left
					List<TerminalTreebankNode> precedingPreterms = JCasUtil.selectPreceding(systemView, TerminalTreebankNode.class, mention, 1);
					if(precedingPreterms != null && precedingPreterms.size() == 1){
						TerminalTreebankNode leftTerm = precedingPreterms.get(0);
						if(leftTerm.getNodeType().equals("DT")){
							// now see if adding this would make it match a tree
							List<TreebankNode> matchingNodes = JCasUtil.selectCovered(systemView, TreebankNode.class, leftTerm.getBegin(), mention.getEnd());
							for(TreebankNode node : matchingNodes){
								if(node.getBegin() == leftTerm.getBegin() && node.getEnd() == mention.getEnd()){
									sameSpanNode = node;
									break;
								}
							}
							if(sameSpanNode != null){
								// adding the DT to the left of th emention made it match a tree:
								System.err.println("Adding DT: " + leftTerm.getCoveredText() + " to TIMEX: " + mention.getCoveredText());
								mention.setBegin(leftTerm.getBegin());
							}
						}
					}
				}
			}
		}
	}


	public static class CopyFromGold extends JCasAnnotator_ImplBase {

		public static AnalysisEngineDescription getDescription(Class<?>... classes)
				throws ResourceInitializationException {
			return AnalysisEngineFactory.createEngineDescription(
					CopyFromGold.class,
					CopyFromGold.PARAM_ANNOTATION_CLASSES,
					classes);
		}

		public static final String PARAM_ANNOTATION_CLASSES = "AnnotationClasses";

		@ConfigurationParameter(name = PARAM_ANNOTATION_CLASSES, mandatory = true)
		private Class<? extends TOP>[] annotationClasses;

		@Override
		public void process(JCas jCas) throws AnalysisEngineProcessException {
			JCas goldView, systemView;
			try {
				goldView = jCas.getView(GOLD_VIEW_NAME);
				systemView = jCas.getView(CAS.NAME_DEFAULT_SOFA);
			} catch (CASException e) {
				throw new AnalysisEngineProcessException(e);
			}
			for (Class<? extends TOP> annotationClass : this.annotationClasses) {
				for (TOP annotation : Lists.newArrayList(JCasUtil.select(systemView, annotationClass))) {
					if (annotation.getClass().equals(annotationClass)) {
						annotation.removeFromIndexes();
					}
				}
			}
			CasCopier copier = new CasCopier(goldView.getCas(), systemView.getCas());
			Feature sofaFeature = jCas.getTypeSystem().getFeatureByFullName(CAS.FEATURE_FULL_NAME_SOFA);
			for (Class<? extends TOP> annotationClass : this.annotationClasses) {
				for (TOP annotation : JCasUtil.select(goldView, annotationClass)) {
					TOP copy = (TOP) copier.copyFs(annotation);
					if (copy instanceof Annotation) {
						copy.setFeatureValue(sofaFeature, systemView.getSofa());
					}
					copy.addToIndexes(systemView);
				}
			}
		}
	}

	public static class WriteI2B2XML extends JCasAnnotator_ImplBase {
		public static final String PARAM_OUTPUT_DIR="PARAM_OUTPUT_DIR";
		@ConfigurationParameter(mandatory=true,description="Output directory to write xml files to.",name=PARAM_OUTPUT_DIR)
		protected String outputDir;

		@Override
		public void process(JCas jcas) throws AnalysisEngineProcessException {
			try {
				// get the output file name from the input file name and output directory.
				File outDir = new File(outputDir);
				if(!outDir.exists()) outDir.mkdirs();
				File inFile = new File(ViewUriUtil.getURI(jcas));
				String outFile = inFile.getName().replace(".txt", "");

				// build the xml
				DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
				Document doc = docBuilder.newDocument();
				Element rootElement = doc.createElement("ClinicalNarrativeTemporalAnnotation");
				Element textElement = doc.createElement("TEXT");
				Element tagsElement = doc.createElement("TAGS");
				textElement.setTextContent(jcas.getDocumentText());
				rootElement.appendChild(textElement);
				rootElement.appendChild(tagsElement);
				doc.appendChild(rootElement);

				Map<IdentifiedAnnotation,String> argToId = new HashMap<>();
				int id=0;
				for(TimeMention timex : JCasUtil.select(jcas, TimeMention.class)){
					Element timexElement = doc.createElement("TIMEX3");
					String timexID = "T"+id; id++;
					argToId.put(timex, timexID);
					timexElement.setAttribute("id", timexID);
					timexElement.setAttribute("start", String.valueOf(timex.getBegin()+1));
					timexElement.setAttribute("end", String.valueOf(timex.getEnd()+1));
					timexElement.setAttribute("text", timex.getCoveredText());
					timexElement.setAttribute("type", "NA");
					timexElement.setAttribute("val", "NA");
					timexElement.setAttribute("mod", "NA");
					tagsElement.appendChild(timexElement);
				}

				id = 0;
				for(EventMention event : JCasUtil.select(jcas, EventMention.class)){
					if (event.getClass().equals(EventMention.class)) {
						// this ensures we are only looking at THYME events and not ctakes-dictionary-lookup events
						Element eventEl = doc.createElement("EVENT");
						String eventID = "E"+id;  id++;
						argToId.put(event, eventID);
						eventEl.setAttribute("id", eventID);
						eventEl.setAttribute("start", String.valueOf(event.getBegin()+1));
						eventEl.setAttribute("end", String.valueOf(event.getEnd()+1));
						eventEl.setAttribute("text", event.getCoveredText());
						eventEl.setAttribute("modality", "NA");
						eventEl.setAttribute("polarity", "NA");
						eventEl.setAttribute("type", "NA");
						tagsElement.appendChild(eventEl);
					}
				}

				id = 0;
				for(TemporalTextRelation rel : JCasUtil.select(jcas, TemporalTextRelation.class)){
					Element linkEl = doc.createElement("TLINK");
					String linkID = "TL"+id; id++;
					linkEl.setAttribute("id", linkID);
					Annotation arg1 = rel.getArg1().getArgument();
					linkEl.setAttribute("fromID", argToId.get(arg1));
					linkEl.setAttribute("fromText", arg1.getCoveredText());
					Annotation arg2 = rel.getArg2().getArgument();
					if(arg2!=null){
						linkEl.setAttribute("toID", argToId.get(arg2));
						linkEl.setAttribute("toText", arg2.getCoveredText());
					}else{
						linkEl.setAttribute("toID", "Discharge");
						linkEl.setAttribute("toText", "Discharge");
					}
					linkEl.setAttribute("type", rel.getCategory());
					tagsElement.appendChild(linkEl);
				}

				// boilerplate xml-writing code:
				TransformerFactory transformerFactory = TransformerFactory.newInstance();
				Transformer transformer = transformerFactory.newTransformer();
				transformer.setOutputProperty(OutputKeys.INDENT, "yes");
				transformer.setOutputProperty(OutputKeys.METHOD, "xml");
				DOMSource source = new DOMSource(doc);
				StreamResult result = new StreamResult(new File(outputDir, outFile));
				transformer.transform(source, result);
			} catch (ParserConfigurationException e) {
				e.printStackTrace();
				throw new AnalysisEngineProcessException(e);
			} catch (TransformerConfigurationException e) {
				e.printStackTrace();
				throw new AnalysisEngineProcessException(e);
			} catch (TransformerException e) {
				e.printStackTrace();
				throw new AnalysisEngineProcessException(e);
			}

		}

	}
}
