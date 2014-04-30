package chord.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Iterator;

/**
 * Implementation for indexing a set of objects by the order in which the objects are added to the set.
 * <p>
 * Maintains an array list and a hash set.
 * <p>
 * Provides O(1) access to the object at a given index by maintaining an array list.
 * <p>
 * Provides O(1) membership testing for a given object by maintaining a hash set.
 * 
 * @param <T> The type of objects in the set.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class IndexSet<T> implements Iterable<T> {
    protected final List<T> list;
    protected final HashSet<T> hset;
    public IndexSet(int size) {
        list = new ArrayList<T>(size);
        hset = new HashSet<T>(size);
    }
    public IndexSet() {
        list = new ArrayList<T>();
        hset = new HashSet<T>();
    }
    /**
     * Remove all elements from the set.
     */
    public void clear() {
        list.clear();
        hset.clear();
    }
    public boolean contains(Object val) {
        return hset.contains(val);
    }
    /**
     * Adds a given object, unless it already exists, in O(1) time.
     * 
     * @param val An object.
     * 
     * @return true iff the given object did not already exist and was successfully added.
     */
    public boolean add(T val) {
        if (hset.add(val)) {
            list.add(val);
            return true;
        }
        return false;
    }
    public int size() {
        return list.size();
    }
    public Iterator<T> iterator() {
        return new Itr();
    }
    private class Itr implements Iterator<T> {
        int cursor = 0;
        public boolean hasNext() {
            return cursor != list.size();
        }
        public T next() {
            return list.get(cursor++);
        }
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
