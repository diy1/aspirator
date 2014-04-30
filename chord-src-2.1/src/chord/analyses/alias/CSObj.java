package chord.analyses.alias;

import java.util.Set;
import java.io.Serializable;

/**
 * Representation of an object-sensitive abstract object.
 * <p>
 * It is a set of abstract contexts (see {@link chord.analyses.alias.Ctxt}).
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class CSObj implements Serializable {
    public final Set<Ctxt> pts;
    public CSObj(Set<Ctxt> pts) {
        assert (pts != null);
        this.pts = pts;
    }
    /**
     * Determines whether this abstract object may alias with a given abstract object.
     * 
     * @param that An abstract object.
     * 
     * @return true iff this abstract object may alias with the given abstract object.
     */
    public boolean mayAlias(CSObj that) {
        for (Ctxt e : pts) {
            if (that.pts.contains(e))
                return true;
        }
        return false;
    }
    public int hashCode() {
        return pts.hashCode();
    }
    public boolean equals(Object that) {
        if (that instanceof CSObj)
            return pts.equals(((CSObj) that).pts);
        return false;
    }
    public String toString() {
        String s = "[";
        for (Ctxt e : pts) {
            s += " " + e;
        }
        return s + " ]";
    }
}
