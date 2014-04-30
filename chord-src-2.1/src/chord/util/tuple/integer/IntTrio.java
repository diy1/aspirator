package chord.util.tuple.integer;

/**
 * An ordered 3-tuple of integers.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class IntTrio implements java.io.Serializable {
    private static final long serialVersionUID = 1728579358137591136L;
    /**
     * The 0th integer in the ordered 3-tuple.
     */
    public int idx0;
    /**
     * The 1st integer in the ordered 3-tuple.
     */
    public int idx1;
    /**
     * The 2nd integer in the ordered 3-tuple.
     */
    public int idx2;
    public IntTrio(int idx0, int idx1, int idx2) {
        this.idx0 = idx0;
        this.idx1 = idx1;
        this.idx2 = idx2;
    }
    public boolean equals(Object o) {
        if (o instanceof IntTrio) {
            IntTrio that = (IntTrio) o;
            return that.idx0 == idx0 && that.idx1 == idx1 && that.idx2 == idx2;
        }
        return false;
    }
    public int hashCode() {
        return idx0 + idx1 + idx2;
    }
    public String toString() {
        return "<" + idx0 + ", " + idx1 + ", " + idx2 + ">";
    }
}
