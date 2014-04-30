package chord.analyses.escape.hybrid.full;

import java.util.Arrays;
import chord.util.ArraySet;

/**
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class SrcNode {
    public final Obj[] env;
    public final ArraySet<FldObj> heap;
    public SrcNode(Obj[] env, ArraySet<FldObj> heap) {
        this.env = env;
        this.heap = heap;
    }
    public int hashCode() {
        int i = 5381;
        for (Obj pts : env) {
            i = ((i << 5) + i) + pts.hashCode();
        }
        return i;
    }
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof SrcNode)) return false;
        SrcNode that = (SrcNode) o;
        return Arrays.equals(env, that.env) && heap.equals(that.heap);
    }
    public String toString() {
        return "v@s=" + ThreadEscapeFullAnalysis.toString(env) +
             "; h@s=" + ThreadEscapeFullAnalysis.toString(heap);
    }
}
