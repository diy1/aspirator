package chord.analyses.heapacc;

import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.ALoad;
import joeq.Compiler.Quad.Operator.AStore;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each quad that accesses (reads or writes) an array element
 * (as opposed to an instance or static field).
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "aryElemE",
    sign = "E0"
)
public class RelAryElemE extends ProgramRel {
    public void fill() {
        DomE domE = (DomE) doms[0];
        int numE = domE.size();
        for (int eIdx = 0; eIdx < numE; eIdx++) {
            Quad e = (Quad) domE.get(eIdx);
            Operator op = e.getOperator();
            if (op instanceof ALoad || op instanceof AStore) {
                add(eIdx);
            }
        }
    }
}
