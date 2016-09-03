package org.apache.ctakes.dictionary.lookup2.concept;

import org.apache.ctakes.dictionary.lookup2.util.SemanticUtil;
import org.apache.ctakes.dictionary.lookup2.util.collection.CollectionMap;
import org.apache.ctakes.dictionary.lookup2.util.collection.EnumSetMap;
import org.apache.ctakes.dictionary.lookup2.util.collection.ImmutableCollectionMap;
import org.apache.ctakes.typesystem.type.constants.CONST;

import javax.annotation.concurrent.Immutable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

/**
 * Author: SPF
 * Affiliation: CHIP-NLP
 * Date: 11/20/13
 */
@Immutable
final public class Concept {

   static public final String PREFERRED_TERM_UNKNOWN = "Unknown Preferred Term";

   final private String _cui;
   final private String _preferredText;
   final private CollectionMap<ConceptCode, String, ? extends Collection<String>> _codes;
   final private Collection<Integer> _ctakesSemantics;

   final private int _hashcode;

   public Concept( final String cui ) {
      this( cui, "" );
   }

   public Concept( final String cui, final String preferredText ) {
      this( cui, preferredText, new EnumSetMap<ConceptCode, String>( ConceptCode.class ) );
   }

   public Concept( final String cui, final String preferredText,
                   final CollectionMap<ConceptCode, String, ? extends Collection<String>> codes ) {
      _cui = cui;
      _preferredText = preferredText;
      _codes = new ImmutableCollectionMap<>( codes );
      final Collection<Integer> ctakesSemantics = new HashSet<>();
      for ( String tui : getCodes( ConceptCode.TUI ) ) {
         // Attempt to obtain one or more valid type ids from the tuis of the term
         ctakesSemantics.add( SemanticUtil.getTuiSemanticGroupId( tui ) );
      }
      if ( ctakesSemantics.isEmpty() ) {
         ctakesSemantics.add( CONST.NE_TYPE_ID_UNKNOWN );
      }
      _ctakesSemantics = Collections.unmodifiableCollection( ctakesSemantics );
      _hashcode = (cui + "_" + preferredText + "_" + codes.hashCode()).hashCode();
   }

   public String getCui() {
      return _cui;
   }

   public String getPreferredText() {
      if ( _preferredText != null ) {
         return _preferredText;
      }
      return PREFERRED_TERM_UNKNOWN;
   }

   public Collection<String> getCodes( final ConceptCode codeType ) {
      return _codes.getCollection( codeType );
   }

   /**
    * @return the type of term that exists in the dictionary: Anatomical Site, Disease/Disorder, Drug, etc.
    */
   public Collection<Integer> getCtakesSemantics() {
      return _ctakesSemantics;
   }

   public boolean isEmpty() {
      return (_preferredText == null || _preferredText.isEmpty()) && _codes.isEmpty();
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public boolean equals( final Object value ) {
      return value instanceof Concept
             && _cui.equals( ((Concept)value)._cui )
             && _preferredText.equals( ((Concept)value)._preferredText )
             && _codes.equals( ((Concept)value)._codes );
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int hashCode() {
      return _hashcode;
   }

}
