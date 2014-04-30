package chord.analyses.lock;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Inst;
import chord.analyses.method.DomM;
import chord.program.Program;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (m,l) such that method m contains
 * lock acquisition point l.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "ML",
    sign = "M0,L0:M0_L0"
)
public class RelML extends ProgramRel {
    public void fill() {
        DomM domM = (DomM) doms[0];
        DomL domL = (DomL) doms[1];
        int numL = domL.size();
        for (int lIdx = 0; lIdx < numL; lIdx++) {
            Inst i = domL.get(lIdx);
            jq_Method m = i.getMethod();
            int mIdx = domM.indexOf(m);
            assert (mIdx >= 0);
            add(mIdx, lIdx);
        }
    }
}
