package chord.util.tuple.object;

import chord.util.Utils;

/**
 * An ordered 2-tuple of objects.
 * 
 * @param    <T0>    The type of the 0th object in the ordered 2-tuple.
 * @param    <T1>    The type of the 1st object in the ordered 2-tuple.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class Pair<T0, T1> implements java.io.Serializable {
    private static final long serialVersionUID = -8922893589568667796L;
    /**
     * The 0th object in the ordered 2-tuple.
     */
    public T0 val0;
    /**
     * The 1st object in the ordered 2-tuple.
     */
    public T1 val1;
    public Pair(T0 val0, T1 val1) {
        this.val0 = val0;
        this.val1 = val1;
    }
    public boolean equals(Object o) {
        if (o instanceof Pair) {
            Pair that = (Pair) o;
            return Utils.areEqual(this.val0, that.val0) &&
                   Utils.areEqual(this.val1, that.val1);
        }
        return false;
    }
    public int hashCode() {
        return (val0 == null ? 0 : val0.hashCode()) +
               (val1 == null ? 0 : val1.hashCode());
    }
    public String toString() {
        return "<" + val0 + ", " + val1 + ">";
    }
}
