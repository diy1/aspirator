package chord.analyses.heapacc;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import chord.analyses.method.DomM;
import chord.program.Program;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (m,e) such that method m contains quad e that accesses
 * (reads or writes) an instance field, a static field, or an array element.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "ME",
    sign = "M0,E0:E0_M0"
)
public class RelME extends ProgramRel {
    public void fill() {
        DomM domM = (DomM) doms[0];
        DomE domE = (DomE) doms[1];
        int numE = domE.size();
        for (int hIdx = 0; hIdx < numE; hIdx++) {
            Quad q = (Quad) domE.get(hIdx);
            jq_Method m = q.getMethod();
            int mIdx = domM.indexOf(m);
            assert (mIdx >= 0);
            add(mIdx, hIdx);
        }
    }
}
