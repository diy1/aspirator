package chord.util.tuple.object;

import chord.util.Utils;

/**
 * An ordered 3-tuple of objects.
 * 
 * @param    <T0>    The type of the 0th object in the 3-tuple.
 * @param    <T1>    The type of the 1st object in the 3-tuple.
 * @param    <T2>    The type of the 2nd object in the 3-tuple.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class Trio<T0, T1, T2> implements java.io.Serializable {
    private static final long serialVersionUID = -4721611129655917301L;
    /**
     * The 0th object in the ordered 3-tuple.
     */
    public T0 val0;
    /**
     * The 1st object in the ordered 3-tuple.
     */
    public T1 val1;
    /**
     * The 2nd object in the ordered 3-tuple.
     */
    public T2 val2;
    public Trio(T0 val0, T1 val1, T2 val2) {
        this.val0 = val0;
        this.val1 = val1;
        this.val2 = val2;
    }
    public boolean equals(Object o) {
        if (o instanceof Trio) {
            Trio that = (Trio) o;
            return Utils.areEqual(this.val0, that.val0) &&
                   Utils.areEqual(this.val1, that.val1) &&
                   Utils.areEqual(this.val2, that.val2);
        }
        return false;
    }
    public int hashCode() {
        return (val0 == null ? 0 : val0.hashCode()) +
               (val1 == null ? 0 : val1.hashCode()) +
               (val2 == null ? 0 : val2.hashCode());
    }
    public String toString() {
        return "<" + val0 + ", " + val1 + ", " + val2 + ">";
    }
}
