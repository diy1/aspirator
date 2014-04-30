package chord.util.tuple.integer;

/**
 * An ordered 6-tuple of integers.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class IntHext implements java.io.Serializable {
    private static final long serialVersionUID = -7833966053817524146L;
    /**
     * The 1st integer in the ordered 6-tuple.
     */
    public int idx0;
    /**
     * The 2nd integer in the ordered 6-tuple.
     */
    public int idx1;
    /**
     * The 3rd integer in the ordered 6-tuple.
     */
    public int idx2;
    /**
     * The 4th integer in the ordered 6-tuple.
     */
    public int idx3;
    /**
     * The 5th integer in the ordered 6-tuple.
     */
    public int idx4;
    /**
     * The 6th integer in the ordered 6-tuple.
     */
    public int idx5;
    public IntHext(int idx0, int idx1, int idx2, int idx3, int idx4, int idx5) {
        this.idx0 = idx0;
        this.idx1 = idx1;
        this.idx2 = idx2;
        this.idx3 = idx3;
        this.idx4 = idx4;
        this.idx5 = idx5;
    }
    public boolean equals(Object o) {
        if (o instanceof IntHext) {
            IntHext that = (IntHext) o;
            return that.idx0 == idx0 && that.idx1 == idx1 &&
                   that.idx2 == idx2 && that.idx3 == idx3 &&
                   that.idx4 == idx4 && that.idx5 == idx5;
        }
        return false;
    }
    public int hashCode() {
        return idx0 + idx1 + idx2 + idx3 + idx4 + idx5;
    }
    public String toString() {
        return "<" + idx0 + ", " + idx1 + ", " + idx2 + ", " +
                     idx3 + ", " + idx4 + ", " + idx5 + ">";
    }
}
