package org.apache.ctakes.dictionary.lookup2.util;

import java.util.Collection;
import java.util.HashSet;

/**
 * Author: SPF
 * Affiliation: CHIP-NLP
 * Date: 9/5/2014
 */
final public class CuiCodeUtil {

   private CuiCodeUtil() {
   }

   static public String getAsCui( final Long code ) {
      final StringBuilder sb = new StringBuilder( 8 );
      sb.append( code );
      return getAsCui( sb );
   }

   static public String getAsCui( final String code ) {
      if ( code.length() == 8 && code.startsWith( "C" ) ) {
         return code;
      }
      final StringBuilder sb = new StringBuilder( 8 );
      sb.append( code.replace( "C", "" ) );
      return getAsCui( sb );
   }

   static private String getAsCui( final StringBuilder sb ) {
      while ( sb.length() < 7 ) {
         sb.insert( 0, '0' );
      }
      sb.insert( 0, 'C' );
      return sb.toString();
   }


   static public Long getCuiCode( final String cui ) {
      final String cuiText = getAsCui( cui );
      final String cuiNum = cuiText.substring( 1, cuiText.length() );
      try {
         return Long.parseLong( cuiNum );
      } catch ( NumberFormatException nfE ) {
         System.err.println( "Could not create Cui Code for " + cui );
      }
      return -1l;
   }

   static public Collection<Long> getCuiCodes( final Collection<String> cuis ) {
      final Collection<Long> cuiCodes = new HashSet<>( cuis.size() );
      for ( String cui : cuis ) {
         cuiCodes.add( getCuiCode( cui ) );
      }
      return cuiCodes;
   }


}
