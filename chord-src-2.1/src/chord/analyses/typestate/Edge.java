package chord.analyses.typestate;

import java.util.HashMap;
import java.util.Map;

import joeq.Compiler.Quad.Quad;
import chord.util.ArraySet;
import chord.project.analyses.rhs.IEdge;
import chord.util.Utils;

/**
 * Representation of path edge or summary edge in type-state analysis.
 * There are 3 kinds of edges with template <srcNode, h, dstNode>:
 * NULL: <null, null, null>
 * ALLOC: <null, h, null> or <null, h, AS>
 * FULL: <AS, h, AS'>
 *
 * @author machiry
 */
public class Edge implements IEdge {
    public static final Edge NULL = new Edge();

    final public EdgeKind type;
    final public Quad h;
    final public AbstractState srcNode;
    public AbstractState dstNode;

    // used only for construction of NULL edge
    protected Edge() {
        type = EdgeKind.NULL;
        h = null;
        srcNode = null;
        dstNode = null;
    }

    // used only for construction of ALLOC or FULL edge (not NULL edge)
    public Edge(AbstractState s, AbstractState d, EdgeKind k, Quad a) {
        assert (a != null);
        if (k == EdgeKind.FULL) {
            assert (s != null);
            assert (d != null);
        } else {
            assert (k == EdgeKind.ALLOC);
            assert (s == null);
        }
        type = k; 
        h = a;
        srcNode = s;
        dstNode = d;
    }

    /**
     * Two path (or summary) edges for the same program point (or method) can be
     * merged if the edges are of the same type, their source nodes match, and
     * one's destination node subsumes the other's.
     */
    @Override
    public int canMerge(IEdge e, boolean mustMerge) {
        assert (!mustMerge);  // not implemented yet
        Edge that = (Edge) e;
        if (this.type != that.type || this.h != that.h) return -1;
        if (this.dstNode != null && that.dstNode != null) {
            if (this.dstNode.canReturn != that.dstNode.canReturn) return -1;
            TypeState thisTs = this.dstNode.ts;
            TypeState thatTs = that.dstNode.ts;
            if (thisTs != thatTs) return -1;
            ArraySet<AccessPath> thisMS = this.dstNode.ms;
            ArraySet<AccessPath> thatMS = that.dstNode.ms;
            if (!thisMS.containsAll(thatMS) && !thatMS.containsAll(thisMS))
                return -1;
        }
        return Utils.areEqual(this.srcNode, that.srcNode) ? 0 : -1;
    }

    @Override
    public boolean mergeWith(IEdge e) {    
        Edge that = (Edge) e;
        if (that.dstNode == null) {
            // 'that' is either NULL:<null,null,null> or ALLOC:<null,h,null>
            return false;
        }
        if (this.dstNode == null) {
            // 'this' is ALLOC:<null,h,null> and 'that' is ALLOC<null,h,AS>
            this.dstNode = that.dstNode;
            return true;
        }
        ArraySet<AccessPath> thisMS = this.dstNode.ms;
        ArraySet<AccessPath> thatMS = that.dstNode.ms;
        if (thatMS.containsAll(thisMS))
            return false;
        this.dstNode = that.dstNode;
        return true;
    }
    
    @Override
    public String toString() {
        String t = "";
        switch(type) {
        case NULL: return "[NULL]";
        case ALLOC: t = "ALLOC"; break;
        case FULL: t = "FULL"; break;
        }
        return "[" + t + ",h=[" + h.toVerboseStr() + "],s=[" + srcNode + "],d=[" + dstNode + "]]";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof Edge) {
            Edge that = (Edge) obj;
            return this.type == that.type && this.h == that.h &&
                Utils.areEqual(this.srcNode, that.srcNode) &&
                Utils.areEqual(this.dstNode, that.dstNode);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return ((h != null) ? h.hashCode() : 0) + 
               ((srcNode != null) ? srcNode.hashCode() : 0) +
               ((dstNode != null) ? dstNode.hashCode() : 0);
    }
}
