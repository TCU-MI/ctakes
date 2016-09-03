package org.apache.ctakes.dictionary.lookup2.consumer;

import org.apache.ctakes.dictionary.lookup2.concept.Concept;
import org.apache.ctakes.dictionary.lookup2.dictionary.RareWordDictionary;
import org.apache.ctakes.dictionary.lookup2.textspan.TextSpan;
import org.apache.ctakes.dictionary.lookup2.util.collection.CollectionMap;
import org.apache.ctakes.dictionary.lookup2.util.collection.HashSetMap;
import org.apache.ctakes.typesystem.type.constants.CONST;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;

import java.util.*;
import java.util.logging.Logger;

/**
 * Refine a collection of dictionary terms to only contain the most specific variations:
 * "colon cancer" instead of "cancer", performed by span inclusion / complete containment, not overlap
 * Also a start at wsd by trim of overlapping terms of conflicting but related semantic group.
 * In this incarnation, any sign / symptom that is within a disease / disorder is assumed to be
 * less specific than the disease disorder and is discarded.
 * In addition, any s/s or d/d that has the same span as an anatomical site is discarded.
 *
 * @author SPF , chip-nlp
 * @version %I%
 * @since 10/24/2014
 */
public class SemanticCleanupTermConsumer extends AbstractTermConsumer {

   static private final Logger LOGGER = Logger.getLogger( "MetaWsdTermConsumer" );

   private final TermConsumer _idHitConsumer;

   public SemanticCleanupTermConsumer( final UimaContext uimaContext, final Properties properties ) {
      super( uimaContext, properties );
      _idHitConsumer = new PrecisionTermConsumer( uimaContext, properties );
   }


   /**
    * Refine a collection of dictionary terms to only contain the most specific variations:
    * "colon cancer" instead of "cancer", performed by span inclusion /complete containment, not overlap.
    * For instance:
    * "54 year old woman with left breast cancer."
    * in the above sentence, "breast" as part of "breast cancer" is an anatomical site and should not be a S/S
    * "Breast:
    * "lump, cyst"
    * in the above, breast is a list header, denoting findings on exam.
    * {@inheritDoc}
    */
   @Override
   public void consumeHits( final JCas jcas,
                            final RareWordDictionary dictionary,
                            final CollectionMap<TextSpan, Long, ? extends Collection<Long>> textSpanCuis,
                            final CollectionMap<Long, Concept, ? extends Collection<Concept>> cuiConcepts )
         throws AnalysisEngineProcessException {
      final String codingScheme = getCodingScheme();
      final Collection<Integer> usedcTakesSemantics = getUsedcTakesSemantics( cuiConcepts );
      final Map<Integer, CollectionMap<TextSpan, Long, ? extends Collection<Long>>> groupedSemanticCuis
            = new HashMap<>();
      // The dictionary may have more than one type, create a map of types to terms and use them all
      for ( Integer cTakesSemantic : usedcTakesSemantics ) {
         final CollectionMap<TextSpan, Long, ? extends Collection<Long>> semanticTerms = new HashSetMap<>();
         for ( Map.Entry<TextSpan, ? extends Collection<Long>> spanCuis : textSpanCuis ) {
            for ( Long cuiCode : spanCuis.getValue() ) {
               final Collection<Concept> concepts = cuiConcepts.getCollection( cuiCode );
               if ( hascTakesSemantic( cTakesSemantic, concepts ) ) {
                  semanticTerms.placeValue( spanCuis.getKey(), cuiCode );
               }
            }
         }
         groupedSemanticCuis.put( cTakesSemantic, semanticTerms );
      }
      // Clean up sign/symptoms and disease/disorder spans that are also anatomical sites
      if ( groupedSemanticCuis.containsKey( CONST.NE_TYPE_ID_ANATOMICAL_SITE ) ) {
         if ( groupedSemanticCuis.containsKey( CONST.NE_TYPE_ID_FINDING ) ) {
            for ( TextSpan anatomicalSpan : groupedSemanticCuis.get( CONST.NE_TYPE_ID_ANATOMICAL_SITE ).keySet() ) {
               groupedSemanticCuis.get( CONST.NE_TYPE_ID_FINDING ).remove( anatomicalSpan );
            }
         }
         if ( groupedSemanticCuis.containsKey( CONST.NE_TYPE_ID_DISORDER ) ) {
            for ( TextSpan anatomicalSpan : groupedSemanticCuis.get( CONST.NE_TYPE_ID_ANATOMICAL_SITE ).keySet() ) {
               groupedSemanticCuis.get( CONST.NE_TYPE_ID_FINDING ).remove( anatomicalSpan );
            }
         }
      }
      // Clean up sign/symptoms that are also within disease/disorder spans
      if ( groupedSemanticCuis.containsKey( CONST.NE_TYPE_ID_FINDING )
           && groupedSemanticCuis.containsKey( CONST.NE_TYPE_ID_DISORDER ) ) {
         final CollectionMap<TextSpan, Long, ? extends Collection<Long>> semanticTerms = new HashSetMap<>();
         for ( Map.Entry<TextSpan, ? extends Collection<Long>> diseases : groupedSemanticCuis
               .get( CONST.NE_TYPE_ID_DISORDER ) ) {
            semanticTerms.addAllValues( diseases.getKey(), diseases.getValue() );
            groupedSemanticCuis.get( CONST.NE_TYPE_ID_FINDING ).remove( diseases.getKey() );
         }
         for ( Map.Entry<TextSpan, ? extends Collection<Long>> findings : groupedSemanticCuis
               .get( CONST.NE_TYPE_ID_FINDING ) ) {
            semanticTerms.addAllValues( findings.getKey(), findings.getValue() );
         }
         // We just created a collection with only the largest Textspans.
         // Any smaller Finding textspans are therefore within a larger d/d textspan and should be removed.
         final CollectionMap<TextSpan, Long, ? extends Collection<Long>> preciseDiseaseTerms
               = PrecisionTermConsumer.createPreciseTerms( semanticTerms );
         final Iterable<TextSpan> findingSpans = new ArrayList<>( groupedSemanticCuis.get( CONST.NE_TYPE_ID_FINDING )
               .keySet() );
         for ( TextSpan findingSpan : findingSpans ) {
            if ( !preciseDiseaseTerms.containsKey( findingSpan ) ) {
               groupedSemanticCuis.get( CONST.NE_TYPE_ID_FINDING ).remove( findingSpan );
            }
         }
      }
      for ( Map.Entry<Integer, CollectionMap<TextSpan, Long, ? extends Collection<Long>>> group : groupedSemanticCuis
            .entrySet() ) {
         consumeTypeIdHits( jcas, codingScheme, group.getKey(),
               PrecisionTermConsumer.createPreciseTerms( group.getValue() ), cuiConcepts );
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void consumeTypeIdHits( final JCas jcas, final String codingScheme, final int cTakesSemantic,
                                  final CollectionMap<TextSpan, Long, ? extends Collection<Long>> semanticTerms,
                                  final CollectionMap<Long, Concept, ? extends Collection<Concept>> conceptMap )
         throws AnalysisEngineProcessException {
      _idHitConsumer.consumeTypeIdHits( jcas, codingScheme, cTakesSemantic, semanticTerms, conceptMap );
   }


}
