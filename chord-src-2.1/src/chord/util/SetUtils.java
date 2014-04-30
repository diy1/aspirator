package chord.util;

import java.util.HashSet;
import java.util.Set;

/**
 * Set related utilities.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class SetUtils {
    /**
     * Default estimated size of a set.
     */
    public static final int DEFAULT_ESTIMATED_SIZE = 10;
    /**
     * Threshold that, given the estimated size of a set to be created, is used to decide whether to use a lightweight
     * (e.g. ArraySet) or heavyweight (e.g. HashSet) implementation.
     */
    public static final int THRESHOLD = 10;
    /**
     * Create a new set of the default estimated size.
     * {@link #THRESHOLD} is used to choose the implementation of the created set based on the default estimated size.
     *
     * @param <T> The type of elements in the set.
     * @return The created set.
     */
    public static <T> Set<T> newSet() {
        return newSet(DEFAULT_ESTIMATED_SIZE);
    }
    /**
     * Create a new set of the given estimated size.
     * {@link #THRESHOLD} is used to choose the implementation of the create set based on the given estimated size.
     * 
     * @param <T> The type of elements in the set.
     * @param size The estimated size of the set.
     * @return The created set.
     */
    public static <T> Set<T> newSet(int size) {
        return (size < THRESHOLD) ? new ArraySet<T>(size) : new HashSet<T>(size);
    }
    /**
     * Create a copy of the given set.
     * {@link #THRESHOLD} is used to choose the implementation of the created set based on the size of the given set.
     * 
     * @param <T> The type of elements in the given set.
     * @param c The given set to be copied.
     * @return The created set containing the elements in the given set.
     */
    public static <T> Set<T> newSet(Set<T> c) {
        Set<T> set = newSet(c.size());
        set.addAll(c);
        return set;
    }
    /**
     * Create a set from the given iterable of the given estimated size.
     * {@link #THRESHOLD} is used to choose the implementation of the created set based on the given estimated size.
     * 
     * @param <T> The type of elements in the iterable.
     * @param c The iterable.
     * @param size The estimated size of the iterable.
     * @return The created set containing the elements in the iterable.
     */
    public static <T> Set<T> iterableToSet(Iterable<T> c, int size) {
        Set<T> set = newSet(size);
        for (T e : c) set.add(e);
        return set;
    }
}
