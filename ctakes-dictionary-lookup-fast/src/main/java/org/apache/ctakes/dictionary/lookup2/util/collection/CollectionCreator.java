package org.apache.ctakes.dictionary.lookup2.util.collection;

import java.util.Collection;

/**
 * @author SPF , chip-nlp
 * @version %I%
 * @since 9/23/2014
 */
public interface CollectionCreator<V, T extends Collection<V>> {

   public T createCollection();

   public T createCollection( int size );


}
