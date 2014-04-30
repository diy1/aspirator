package chord.util.tuple.object;

import chord.util.Utils;

/**
 * An ordered 6-tuple of objects.
 * 
 * @param    <T0>    The type of the 0th object in the 6-tuple.
 * @param    <T1>    The type of the 1st object in the 6-tuple.
 * @param    <T2>    The type of the 2nd object in the 6-tuple.
 * @param    <T3>    The type of the 3rd object in the 6-tuple.
 * @param    <T4>    The type of the 4th object in the 6-tuple.
 * @param    <T5>    The type of the 5th object in the 6-tuple.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class Hext<T0, T1, T2, T3, T4, T5> implements java.io.Serializable {
    private static final long serialVersionUID = -3528405999768010501L;
    /**
     * The 0th object in the ordered 6-tuple.
     */
    public T0 val0;
    /**
     * The 1st object in the ordered 6-tuple.
     */
    public T1 val1;
    /**
     * The 2nd object in the ordered 6-tuple.
     */
    public T2 val2;
    /**
     * The 3rd object in the ordered 6-tuple.
     */
    public T3 val3;
    /**
     * The 4th object in the ordered 6-tuple.
     */
    public T4 val4;
    /**
     * The 5th object in the ordered 6-tuple.
     */
    public T5 val5;
    public Hext(T0 val0, T1 val1, T2 val2, T3 val3, T4 val4, T5 val5) {
        this.val0 = val0;
        this.val1 = val1;
        this.val2 = val2;
        this.val3 = val3;
        this.val4 = val4;
        this.val5 = val5;
    }
    public boolean equals(Object o) {
        if (o instanceof Hext) {
            Hext that = (Hext) o;
            return Utils.areEqual(this.val0, that.val0) &&
                   Utils.areEqual(this.val1, that.val1) &&
                   Utils.areEqual(this.val2, that.val2) &&
                   Utils.areEqual(this.val3, that.val3) &&
                   Utils.areEqual(this.val4, that.val4) &&
                   Utils.areEqual(this.val5, that.val5);
        }
        return false;
    }
    public int hashCode() {
        return (val0 == null ? 0 : val0.hashCode()) +
               (val1 == null ? 0 : val1.hashCode()) +
               (val2 == null ? 0 : val2.hashCode()) +
               (val3 == null ? 0 : val3.hashCode()) +
               (val4 == null ? 0 : val4.hashCode()) +
               (val5 == null ? 0 : val5.hashCode());
    }
    public String toString() {
        return "<" + val0 + ", " + val1 + ", " + val2 + ", " + val3 + ", " + val4 + ", " + val5 + ">";
    }
}
