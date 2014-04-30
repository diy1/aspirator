package chord.analyses.reflect;

import joeq.Class.jq_Reference;
import joeq.Class.jq_Method;

import chord.analyses.alloc.DomH;
import chord.analyses.type.DomT;
import chord.program.PhantomClsVal;
import chord.program.Program;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (t,h) such that h is the hypothetical
 * site at which class t is reflectively created.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "clsTH",
    sign = "T0,H0:H0_T0"
)
public class RelClsTH extends ProgramRel {
    public void fill() {
        DomT domT = (DomT) doms[0];
        DomH domH = (DomH) doms[1];
        for (jq_Reference r : Program.g().getClasses()) {
            int tIdx = domT.indexOf(r);
            assert (tIdx >= 0);
            int hIdx = domH.indexOf(new PhantomClsVal(r));
            assert (hIdx >= 0);
            add(tIdx, hIdx);
        }
    }
}
