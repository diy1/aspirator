package chord.analyses.lock;

import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.Monitor;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (l,v) such that monitorenter quad l
 * is synchronized on variable v.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "syncLV",
    sign = "L0,V0:L0_V0"
)
public class RelSyncLV extends ProgramRel {
    public void fill() {
        DomL domL = (DomL) doms[0];
        int numL = domL.size();
        for (int lIdx = 0; lIdx < numL; lIdx++) {
            Inst i = domL.get(lIdx);
            if (i instanceof Quad) {
                Quad q = (Quad) i;
                Operand op = Monitor.getSrc(q);
                if (op instanceof RegisterOperand) {
                    RegisterOperand ro = (RegisterOperand) op;
                    Register v = ro.getRegister();
                    add(q, v);
                }
            }
        }
    }
}
