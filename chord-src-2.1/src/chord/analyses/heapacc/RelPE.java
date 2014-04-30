package chord.analyses.heapacc;

import joeq.Compiler.Quad.Quad;
import chord.analyses.point.DomP;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (p,e) such that the quad at program point p is
 * a heap-accessing quad e that accesses (reads or writes) an instance field, a
 * static field, or an array element.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "PE",
    sign = "P0,E0:E0_P0"
)
public class RelPE extends ProgramRel {
    public void fill() {
        DomP domP = (DomP) doms[0];
        DomE domE = (DomE) doms[1];
        int numE = domE.size();
        for (int eIdx = 0; eIdx < numE; eIdx++) {
            Quad e = (Quad) domE.get(eIdx);
            int pIdx = domP.indexOf(e);
            assert (pIdx >= 0);
            add(pIdx, eIdx);
        }
    }
}
