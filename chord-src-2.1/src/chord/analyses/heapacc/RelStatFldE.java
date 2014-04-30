package chord.analyses.heapacc;

import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.Getstatic;
import joeq.Compiler.Quad.Operator.Putstatic;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each quad that accesses (reads or writes) a static field
 * (as opposed to an instance field or array element).
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "statFldE",
    sign = "E0"
)
public class RelStatFldE extends ProgramRel {
    public void fill() {
        DomE domE = (DomE) doms[0];
        int numE = domE.size();
        for (int eIdx = 0; eIdx < numE; eIdx++) {
            Quad e = (Quad) domE.get(eIdx);
            Operator op = e.getOperator();
            if (op instanceof Getstatic || op instanceof Putstatic) {
                add(eIdx);
            }
        }
    }
}
