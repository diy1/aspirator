package chord.analyses.heapacc;

import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import chord.program.Program;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing all quads that write to an instance field,
 * a static field, or an array element.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "writeE",
    sign = "E0"
)
public class RelWriteE extends ProgramRel {
    public void fill() {
        DomE domE = (DomE) doms[0];
        int numE = domE.size();
        for (int eIdx = 0; eIdx < numE; eIdx++) {
            Quad e = (Quad) domE.get(eIdx);
            Operator op = e.getOperator();
            if (op.isWrHeapInst())
                add(eIdx);
        }
    }
}
