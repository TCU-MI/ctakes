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
package org.apache.ctakes.dictionary.lookup2.util;

import org.apache.ctakes.dictionary.lookup2.term.RareWordTerm;

import java.util.List;

/**
 * Utility class with methods for matching tokens to valid terms
 * <p/>
 * Author: SPF
 * Affiliation: CHIP-NLP
 * Date: 2/25/14
 */
final public class TokenMatchUtil {

   private TokenMatchUtil() {
   }


   /**
    * Hopefully the jit will inline this method
    *
    * @param rareWordHit    rare word term to check for match
    * @param allTokens      all tokens in a window
    * @param termStartIndex index of first token in allTokens to check
    * @param termEndIndex   index of last token in allTokens to check
    * @return true if the rare word term exists in allTokens within the given indices
    */
   public static boolean isTermMatch( final RareWordTerm rareWordHit, final List<FastLookupToken> allTokens,
                                      final int termStartIndex, final int termEndIndex ) {
      final char[] rareWordHitChars = rareWordHit.getText().toCharArray();
      int hitCharIndex = 0;
      for ( int i = termStartIndex; i < termEndIndex + 1; i++ ) {
         final char[] tokenChars = allTokens.get( i ).getText().toCharArray();
         if ( isTokenMatch( rareWordHitChars, hitCharIndex, tokenChars ) ) {
            // the normal token matched, move to the next token
            hitCharIndex += tokenChars.length + 1;
            continue;
         }
         if ( allTokens.get( i ).getVariant() == null ) {
            // the token normal didn't match and there is no variant
            return false;
         }
         final char[] variantChars = allTokens.get( i ).getVariant().toCharArray();
         if ( isTokenMatch( rareWordHitChars, hitCharIndex, variantChars ) ) {
            // the variant matched, move to the next token
            hitCharIndex += variantChars.length + 1;
            continue;
         }
         // the normal token didn't match and the variant didn't match
         return false;
      }
      // some combination of token and variant matched
      return true;
   }

   /**
    * Check the rare word term to see if a given token is at a given index within that term
    * Hopefully the jit will inline this method
    *
    * @param rareWordHitChars character array of all characters for the entire possible term (all words)
    * @param hitCharIndex     character index in rare word term to check for token
    * @param tokenChars       character array of the search token
    * @return true if rareWordHitChars contains tokenChars at location hitCharIndex
    */
   static private boolean isTokenMatch( final char[] rareWordHitChars, final int hitCharIndex,
                                        final char[] tokenChars ) {
      if ( hitCharIndex + tokenChars.length > rareWordHitChars.length ) {
         return false;
      }
      for ( int tokenCharIndex = 0; tokenCharIndex < tokenChars.length; tokenCharIndex++ ) {
         if ( tokenChars[ tokenCharIndex ] != rareWordHitChars[ hitCharIndex + tokenCharIndex ] ) {
            return false;
         }
      }
      return true;
   }


}
