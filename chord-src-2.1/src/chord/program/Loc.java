package chord.program;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.EntryOrExitBasicBlock;

/**
 * Representation of the location of a statement.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class Loc {
    // qIdx != -1 <=> i is instanceof Quad and qIdx denotes 0-based index in its containing basic block
    // qIdx == -1 <=> i is instanceof EntryOrExitBasicBlock
    public final Inst i;
    public final int qIdx;

    public Loc(Inst i, int qIdx) {
        if (qIdx == -1)
            assert (i instanceof EntryOrExitBasicBlock);
        else {
            assert (qIdx >= 0);
            assert (i instanceof Quad);
        }
        this.i = i;
        this.qIdx = qIdx;
    }

    public int hashCode() { return i.hashCode(); }

    public boolean equals(Object o) {
        if (!(o instanceof Loc)) return false;
        Loc that = (Loc) o;
        return this.i == that.i;
    }

    public String toString() {
        return "<" + i.getMethod() + ", " + i + ">";
    }
}

