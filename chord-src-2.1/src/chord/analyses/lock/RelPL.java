package chord.analyses.lock;

import joeq.Compiler.Quad.Inst;
import chord.analyses.point.DomP;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (p,e) such that the quad at
 * program point p is a heap-accessing quad e that accesses
 * (reads or writes) an instance field, a static field, or an
 * array element.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "PL",
    sign = "P0,L0:P0xL0"
)
public class RelPL extends ProgramRel {
    public void fill() {
        DomP domP = (DomP) doms[0];
        DomL domL = (DomL) doms[1];
        int numL = domL.size();
        for (int lIdx = 0; lIdx < numL; lIdx++) {
            Inst i = domL.get(lIdx);
            int pIdx = domP.indexOf(i);
            assert (pIdx >= 0);
            add(pIdx, lIdx);
        }
    }
}
