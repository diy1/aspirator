package chord.analyses.lock;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.EntryOrExitBasicBlock;
import chord.analyses.method.DomM;
import chord.program.Program;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (l,m) such that method m is
 * synchronized on the lock it acquires at point l.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "syncLM",
    sign = "L0,M0:L0_M0"
)
public class RelSyncLM extends ProgramRel {
    public void fill() {
        DomL domL = (DomL) doms[0];
        DomM domM = (DomM) doms[1];
        int numL = domL.size();
        for (int lIdx = 0; lIdx < numL; lIdx++) {
            Inst i = domL.get(lIdx);
            if (i instanceof EntryOrExitBasicBlock) {
                jq_Method m = i.getMethod();
                int mIdx = domM.indexOf(m);
                assert (mIdx >= 0);
                add(lIdx, mIdx);
            }
        }
    }
}
