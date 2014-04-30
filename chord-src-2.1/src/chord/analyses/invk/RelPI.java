package chord.analyses.invk;

import joeq.Compiler.Quad.Quad;
import chord.analyses.point.DomP;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (p,i) such that the quad at program point p
 * is method invocation quad i.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "PI",
    sign = "P0,I0:I0xP0"
)
public class RelPI extends ProgramRel {
    public void fill() {
        DomP domP = (DomP) doms[0];
        DomI domI = (DomI) doms[1];
        int numI = domI.size();
        for (int iIdx = 0; iIdx < numI; iIdx++) {
            Quad i = (Quad) domI.get(iIdx);
            int pIdx = domP.indexOf(i);
            assert (pIdx >= 0);
            add(pIdx, iIdx);
        }
    }
}
