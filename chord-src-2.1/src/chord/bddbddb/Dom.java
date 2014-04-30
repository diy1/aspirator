package chord.bddbddb;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.File;
import java.io.IOException;

import chord.util.IndexMap;

/**
 * Generic implementation of a BDD-based domain.
 * <p>
 * Typical usage is as follows:
 * <ul>
 * <li> The domain is initialized by calling {@link #setName(String)} which sets the name of the domain. </li>
 * <li> The domain is next built in memory by repeatedly calling {@link #getOrAdd(Object)} with the argument in each call being a value
 * to be added to the domain.  If the value already exists in the domain then the call does not have any effect.  Otherwise, the value
 * is mapped to integer K in the domain where K is the number of values already in the domain. </li>
 * <li> The domain built in memory is reflected onto disk by calling {@link #save(String,boolean)}. </li>
 * <li> The domain on disk can be read by a Datalog program. </li>
 * <li> The domain in memory can be read by calling any of the following:
 * <ul>
 * <li> {@link #iterator()}, which gives an iterator over the values in the domain in memory in the order in which they were added, </li>
 * <li> {@link #get(int)}, which gives the value mapped to the specified integer in the domain in memory, and </li>
 * <li> {@link #indexOf(Object)}, which gives the integer mapped to the specified value in the domain in memory. </li>
 * </ul>
 * </li>
 * </ul>
 *
 * @param <T> The type of values in the domain.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class Dom<T> extends IndexMap<T> {
    protected String name;
    public void setName(String name) {
        assert (name != null);
        assert (this.name == null);
        this.name = name;
    }
    public String getName() {
        return name;
    }
    /**
     * Reflects the domain in memory onto disk.
     */
    public void save(String dirName, boolean saveDomMap) throws IOException {
        String mapFileName = "";
        if (saveDomMap) {
            mapFileName = name + ".map";
            File file = new File(dirName, mapFileName);
            PrintWriter out = new PrintWriter(file);
            int size = size();
                for (int i = 0; i < size; i++) {
                T val = get(i);
                out.println(toUniqueString(val));
            }
            out.close();
        }
        String domFileName = name + ".dom";
        File file = new File(dirName, domFileName);
        PrintWriter out = new PrintWriter(file);
        int size = size();
        out.println(name + " " + size + " " + mapFileName);
        out.close();
    }
    // subclasses may override
    public String toUniqueString(T val) {
        return val == null ? "null" : val.toString();
    }
    public String toUniqueString(int idx) {
        T val = get(idx);
        return toUniqueString(val);
    }
    /**
     * Prints the values in the domain in memory to the standard output stream.
     */
    public void print() {
        print(System.out);
    }
    /**
     * Prints the values in the domain in memory to the specified output stream.
     * 
     * @param out The output stream to which the values in the domain in memory must be printed.
     */
    public void print(PrintStream out) {
        for (int i = 0; i < size(); i++)
            out.println(get(i));
    }
    public int hashCode() {
        return System.identityHashCode(this);
    }
    public boolean equals(Object o) {
        return this == o;
    }
}
