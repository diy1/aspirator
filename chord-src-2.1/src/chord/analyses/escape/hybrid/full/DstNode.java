package chord.analyses.escape.hybrid.full;

import java.util.Arrays;

import chord.util.ArraySet;

/**
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class DstNode {
    public final Obj[] env;
    public final ArraySet<FldObj> heap;
    public final boolean isKill;
    public final boolean isRetn;
    public DstNode(Obj[] env, ArraySet<FldObj> heap, boolean isKill, boolean isRetn) {
        this.env = env;
        this.heap = heap;
        this.isKill = isKill;
        this.isRetn = isRetn;
    }
    public int hashCode() {
        int i = 5381;
        for (Obj pts : env) {
            i = ((i << 5) + i) + pts.hashCode();
        }
        return i;
    }
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof DstNode))
            return false;
        DstNode that = (DstNode) o;
        return Arrays.equals(this.env, that.env)&&heap.equals(that.heap) && isKill == that.isKill 
                && isRetn == that.isRetn;
    }
    public String toString() {
        return "v@d=" + ThreadEscapeFullAnalysis.toString(env) +
             "; h@d=" + ThreadEscapeFullAnalysis.toString(heap) +
             "; k@d=" + isKill + "; r@d: " + isRetn;
    }
}

