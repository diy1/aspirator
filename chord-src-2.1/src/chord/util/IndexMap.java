package chord.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Iterator;

/**
 * Implementation for indexing a set of objects by the order in which the objects are added to the set.
 * <p>
 * Maintains an array list and a hash map.
 * <p>
 * Provides constant-time operations for adding a given object, testing membership of a given object,
 * getting the index of a given object, and getting the object at a given index.
 * <p>
 * Provides O(1) access to the object at a given index by maintaining a list.
 * <p>
 * Provides O(1) membership testing and access to the index of a given object by maintaining a hash map.
 * 
 * @param <T> The type of objects in the set.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class IndexMap<T> implements Iterable<T> {
    protected final List<T> list;
    protected final HashMap<T, Integer> hmap;
    public IndexMap(int size) {
        list = new ArrayList<T>(size);
        hmap = new HashMap<T, Integer>(size);
    }
    public IndexMap() {
        list = new ArrayList<T>();
        hmap = new HashMap<T, Integer>();
    }
    public void clear() {
        list.clear();
        hmap.clear();
    }
    public boolean contains(Object val) {
        return hmap.containsKey(val);
    }
    /**
     * Provides the index of a given object, if it exists, and -1 otherwise in O(1) time.
     * 
     * @param val An object.
     * 
     * @return The index of the given object, if it exists, and -1 otherwise.
     */
    public int indexOf(Object val) {
        Integer idx = hmap.get(val);
        if (idx == null)
            return -1;
        return idx.intValue();
    }
    /**
     * Adds and indexes a given object, unless it already exists, and provides its index in both cases in O(1) time.
     * 
     * @param val An object.
     * 
     * @return The index of the given object.
     */
    public int getOrAdd(T val) {
        Integer idx = hmap.get(val);
        if (idx == null) {
            int i = list.size();
            idx = new Integer(i);
            list.add(val);
            hmap.put(val, idx);
        }
        return idx.intValue();
    }
    /**
     * Adds and indexes a given object, unless it already exists, in O(1) time.
     * 
     * @param val An object.
     * 
     * @return true iff the given object did not already exist and was successfully added and indexed.
     */
    public boolean add(T val) {
        Integer idx = hmap.get(val);
        if (idx == null) {
            int i = list.size();
            list.add(val);
            idx = new Integer(i);
            hmap.put(val, idx);
            return true;
        }
        return false;
    }
    public T get(int idx) {
        return list.get(idx);
    }
    public int size() {
        return list.size();
    }
    public boolean addAll(Collection<? extends T> c) {
        boolean result = false;
        for (T t : c) {
            result |= add(t);
        }
        return result;
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
