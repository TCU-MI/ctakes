package org.apache.ctakes.dictionary.lookup2.ae;

import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.File;
import java.io.FileNotFoundException;


/**
 * @author SPF , chip-nlp
 * @version %I%
 * @since 9/23/2014
 */
final public class DictionaryLookupFactory {

   private DictionaryLookupFactory() {
   }

   public static AnalysisEngineDescription createUmlsDictionaryLookupDescription()
         throws ResourceInitializationException {
      return AnalysisEngineFactory.createEngineDescription( DefaultJCasTermAnnotator.class,
            JCasTermAnnotator.DICTIONARY_DESCRIPTOR_KEY,
            "org/apache/ctakes/dictionary/lookup/fast/cTakesHsql.xml" );
   }

   public static AnalysisEngineDescription createCustomDictionaryLookupDescription( final String dictionaryDescriptor )
         throws ResourceInitializationException {
      if ( !(new File( dictionaryDescriptor )).exists() ) {
         throw new ResourceInitializationException( new FileNotFoundException( dictionaryDescriptor ) );
      }
      return AnalysisEngineFactory.createEngineDescription( DefaultJCasTermAnnotator.class,
            JCasTermAnnotator.DICTIONARY_DESCRIPTOR_KEY,
            dictionaryDescriptor );
   }


}
