package chord.analyses.heapacc;

import joeq.Class.jq_Field;
import joeq.Compiler.Quad.Quad;
import chord.analyses.field.DomF;
import chord.program.Program;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (e,f) such that quad e accesses
 * (reads or writes) instance field, static field, or array element f.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "EF",
    sign = "E0,F0:F0_E0"
)
public class RelEF extends ProgramRel {
    public void fill() {
        DomE domE = (DomE) doms[0];
        DomF domF = (DomF) doms[1];
        int numE = domE.size();
        for (int eIdx = 0; eIdx < numE; eIdx++) {
            Quad e = domE.get(eIdx);
            jq_Field f = e.getField();
            int fIdx = domF.indexOf(f);
            assert (fIdx >= 0);
            add(eIdx, fIdx);
        }
    }
}
