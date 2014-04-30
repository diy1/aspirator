package chord.util.tuple.integer;

/**
 * An ordered 2-tuple of integers.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class IntPair implements java.io.Serializable {
    private static final long serialVersionUID = 6950035759159189325L;
    /**
     * The 0th integer in the ordered 2-tuple.
     */
    public int idx0;
    /**
     * The 1st integer in the ordered 2-tuple.
     */
    public int idx1;
    public IntPair(int idx0, int idx1) {
        this.idx0 = idx0;
        this.idx1 = idx1;
    }
    public boolean equals(Object o) {
        if (o instanceof IntPair) {
            IntPair that = (IntPair) o;
            return this.idx0 == that.idx0 && this.idx1 == that.idx1;
        }
        return false;
    }
    public int hashCode() {
        return idx0 + idx1;
    }
    public String toString() {
        return "<" + idx0 + "," + idx1 + ">";
    }
}
