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
package org.apache.ctakes.dictionary.lookup2.ae;

import org.apache.ctakes.dictionary.lookup2.dictionary.RareWordDictionary;
import org.apache.ctakes.dictionary.lookup2.term.RareWordTerm;
import org.apache.ctakes.dictionary.lookup2.textspan.DefaultTextSpan;
import org.apache.ctakes.dictionary.lookup2.textspan.TextSpan;
import org.apache.ctakes.dictionary.lookup2.util.FastLookupToken;
import org.apache.ctakes.dictionary.lookup2.util.TokenMatchUtil;
import org.apache.ctakes.dictionary.lookup2.util.collection.CollectionMap;

import java.util.Collection;
import java.util.List;

/**
 * A direct string match using phrase permutations
 * Author: SPF
 * Affiliation: CHIP-NLP
 * Date: 11/19/13
 */
final public class DefaultJCasTermAnnotator extends AbstractJCasTermAnnotator {

   /**
    * {@inheritDoc}
    */
   @Override
   public void findTerms( final RareWordDictionary dictionary,
                          final List<FastLookupToken> allTokens,
                          final List<Integer> lookupTokenIndices,
                          final CollectionMap<TextSpan, Long, ? extends Collection<Long>> termsFromDictionary ) {
      Collection<RareWordTerm> rareWordHits;
      for ( Integer lookupTokenIndex : lookupTokenIndices ) {
         final FastLookupToken lookupToken = allTokens.get( lookupTokenIndex );
         rareWordHits = dictionary.getRareWordHits( lookupToken );
         if ( rareWordHits == null || rareWordHits.isEmpty() ) {
            continue;
         }
         for ( RareWordTerm rareWordHit : rareWordHits ) {
            if ( rareWordHit.getText().length() < _minimumLookupSpan ) {
               continue;
            }
            if ( rareWordHit.getTokenCount() == 1 ) {
               // Single word term, add and move on
               termsFromDictionary.placeValue( lookupToken.getTextSpan(), rareWordHit.getCuiCode() );
               continue;
            }
            final int termStartIndex = lookupTokenIndex - rareWordHit.getRareWordIndex();
            if ( termStartIndex < 0 || termStartIndex + rareWordHit.getTokenCount() > allTokens.size() ) {
               // term will extend beyond window
               continue;
            }
            final int termEndIndex = termStartIndex + rareWordHit.getTokenCount() - 1;
            if ( TokenMatchUtil.isTermMatch( rareWordHit, allTokens, termStartIndex, termEndIndex ) ) {
               final int spanStart = allTokens.get( termStartIndex ).getStart();
               final int spanEnd = allTokens.get( termEndIndex ).getEnd();
               termsFromDictionary.placeValue( new DefaultTextSpan( spanStart, spanEnd ), rareWordHit.getCuiCode() );
            }
         }
      }
   }

}
