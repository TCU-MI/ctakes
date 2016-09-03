package org.apache.ctakes.dictionary.lookup2.concept;

import org.apache.ctakes.dictionary.lookup2.util.CuiCodeUtil;
import org.apache.ctakes.dictionary.lookup2.util.LookupUtil;
import org.apache.ctakes.dictionary.lookup2.util.TuiCodeUtil;
import org.apache.ctakes.dictionary.lookup2.util.collection.CollectionMap;
import org.apache.ctakes.dictionary.lookup2.util.collection.EnumSetMap;
import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Author: SPF
 * Affiliation: CHIP-NLP
 * Date: 9/9/2014
 */
final public class BsvConceptFactory implements ConceptFactory {

   static private final Logger LOGGER = Logger.getLogger( "BsvConceptFactory" );

   static private final String BSV_FILE_PATH = "bsvPath";


   final private ConceptFactory _delegateFactory;


   public BsvConceptFactory( final String name, final UimaContext uimaContext, final Properties properties ) {
      this( name, properties.getProperty( BSV_FILE_PATH ) );
   }

   public BsvConceptFactory( final String name, final String bsvFilePath ) {
      this( name, new File( bsvFilePath ) );
   }

   public BsvConceptFactory( final String name, final File bsvFile ) {
      final Collection<CuiTuiTerm> cuiTuiTerms = parseBsvFile( bsvFile );
      final Map<Long, Concept> conceptMap = new HashMap<>( cuiTuiTerms.size() );
      for ( CuiTuiTerm cuiTuiTerm : cuiTuiTerms ) {
         final CollectionMap<ConceptCode, String, ? extends Collection<String>> codes
               = new EnumSetMap<>( ConceptCode.class );
         codes.placeValue( ConceptCode.TUI, TuiCodeUtil.getAsTui( cuiTuiTerm.getTui() ) );
         conceptMap.put( CuiCodeUtil.getCuiCode( cuiTuiTerm.getCui() ),
               new Concept( cuiTuiTerm.getCui(), cuiTuiTerm.getPrefTerm(), codes ) );
      }
      _delegateFactory = new MemConceptFactory( name, conceptMap );
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String getName() {
      return _delegateFactory.getName();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Concept createConcept( final Long cuiCode ) {
      return _delegateFactory.createConcept( cuiCode );
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Map<Long, Concept> createConcepts( final Collection<Long> cuiCodes ) {
      return _delegateFactory.createConcepts( cuiCodes );
   }


   /**
    * Create a collection of {@link org.apache.ctakes.dictionary.lookup2.dictionary.RareWordTermMapCreator.CuiTerm} Objects
    * by parsing a bsv file.  The file can be in one of two columnar formats:
    * <p>
    * CUI|Tui
    * </p>
    * or
    * <p>
    * CUI|TUI|Text
    * </p>
    * or
    * <p>
    * CUI|TUI|Text|PreferredTerm
    * </p>
    * If the TUI column is omitted then the entityId for the dictionary is used as the TUI
    *
    * @param bsvFile file containing term rows and bsv columns
    * @return collection of all valid terms read from the bsv file
    */
   static private Collection<CuiTuiTerm> parseBsvFile( final File bsvFile ) {
      final Collection<CuiTuiTerm> cuiTuiTerms = new ArrayList<>();
      try ( final BufferedReader reader = new BufferedReader( new FileReader( bsvFile ) ) ) {
         String line = reader.readLine();
         while ( line != null ) {
            if ( line.startsWith( "//" ) || line.startsWith( "#" ) ) {
               continue;
            }
            final String[] columns = LookupUtil.fastSplit( line, '|' );
            final CuiTuiTerm cuiTuiTerm = createCuiTuiTerm( columns );
            if ( cuiTuiTerm != null ) {
               // Add to the dictionary
               cuiTuiTerms.add( cuiTuiTerm );
            } else {
               LOGGER.warn( "Bad BSV line " + line + " in " + bsvFile.getPath() );
            }
            line = reader.readLine();
         }
         reader.close();
      } catch ( IOException ioE ) {
         LOGGER.error( ioE.getMessage() );
      }
      return cuiTuiTerms;
   }

   /**
    * @param columns two or three columns representing CUI,Text or CUI,TUI,Text respectively
    * @return a term created from the columns or null if the columns are malformed
    */
   static private CuiTuiTerm createCuiTuiTerm( final String... columns ) {
      if ( columns.length < 2 ) {
         return null;
      }
      final int cuiIndex = 0;
      final int tuiIndex = 1;
      int termIndex = -1;
      if ( columns.length >= 4 ) {
         // third column is text, fourth column is preferred term text
         termIndex = 3;
      }
      if ( columns[ cuiIndex ].trim().isEmpty() || columns[ tuiIndex ].trim().isEmpty() ) {
         return null;
      }
      final String cui = columns[ cuiIndex ];
      // default for an empty tui column is tui 0 = unknown
      final String tui = (columns[ tuiIndex ].trim().isEmpty()) ? "T000" : columns[ tuiIndex ].trim();
      final String term = (termIndex < 0 || columns[ termIndex ].trim().isEmpty()) ? "" : columns[ termIndex ].trim();
      return new CuiTuiTerm( cui, tui, term );
   }

   static public class CuiTuiTerm {

      final private String __cui;
      final private String __tui;
      final private String __prefTerm;
      final private int __hashcode;

      public CuiTuiTerm( final String cui, final String tui, final String preferredTerm ) {
         __cui = cui;
         __tui = tui;
         __prefTerm = preferredTerm;
         __hashcode = (__cui + "_" + __tui + "_" + __prefTerm).hashCode();
      }

      public String getCui() {
         return __cui;
      }

      public String getTui() {
         return __tui;
      }

      public String getPrefTerm() {
         return __prefTerm;
      }

      public boolean equals( final Object value ) {
         return value instanceof CuiTuiTerm
                && __prefTerm.equals( ((CuiTuiTerm)value).__prefTerm )
                && __tui.equals( ((CuiTuiTerm)value).__tui )
                && __cui.equals( ((CuiTuiTerm)value).__cui );
      }

      public int hashCode() {
         return __hashcode;
      }
   }


}
