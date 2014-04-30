package chord.util.tuple.integer;

/**
 * An ordered 4-tuple of integers.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class IntQuad implements java.io.Serializable {
    private static final long serialVersionUID = 8505832814282826196L;
    /**
     * The 0th integer in the ordered 4-tuple.
     */
    public int idx0;
    /**
     * The 1st integer in the ordered 4-tuple.
     */
    public int idx1;
    /**
     * The 2nd integer in the ordered 4-tuple.
     */
    public int idx2;
    /**
     * The 3rd integer in the ordered 4-tuple.
     */
    public int idx3;
    public IntQuad(int idx0, int idx1, int idx2, int idx3) {
        this.idx0 = idx0;
        this.idx1 = idx1;
        this.idx2 = idx2;
        this.idx3 = idx3;
    }
    public boolean equals(Object o) {
        if (o instanceof IntQuad) {
            IntQuad that = (IntQuad) o;
            return that.idx0 == idx0 && that.idx1 == idx1 &&
                   that.idx2 == idx2 && that.idx3 == idx3;
        }
        return false;
    }
    public int hashCode() {
        return idx0 + idx1 + idx2 + idx3;
    }
    public String toString() {
        return "<" + idx0 + ", " + idx1 + ", " +
                     idx2 + ", " + idx3 + ">";
    }
}
