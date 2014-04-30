package chord.analyses.heapacc;

import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.ALoad;
import joeq.Compiler.Quad.Operator.AStore;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Putfield;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.var.DomV;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (e,v) such that quad e accesses
 * (reads or writes) an instance field or array element of an
 * object denoted by local variable v.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "EV",
    sign = "E0,V0:E0_V0"
)
public class RelEV extends ProgramRel {
    public void fill() {
        DomE domE = (DomE) doms[0];
        DomV domV = (DomV) doms[1];
        int numE = domE.size();
        for (int eIdx = 0; eIdx < numE; eIdx++) {
            Quad q = (Quad) domE.get(eIdx);
            Operator op = q.getOperator();
            RegisterOperand bo;
            if (op instanceof ALoad) {
                bo = (RegisterOperand) ALoad.getBase(q);
            } else if (op instanceof Getfield) {
                bo = (RegisterOperand) Getfield.getBase(q);
            } else if (op instanceof AStore) {
                bo = (RegisterOperand) AStore.getBase(q);
            } else if (op instanceof Putfield) {
                bo = (RegisterOperand) Putfield.getBase(q);
            } else
                bo = null;
            if (bo != null) {
                Register b = bo.getRegister();
                int vIdx = domV.indexOf(b);
                assert (vIdx >= 0);
                add(eIdx, vIdx);
            }
        }
    }
}
