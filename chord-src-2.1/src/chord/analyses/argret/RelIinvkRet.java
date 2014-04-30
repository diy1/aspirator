package chord.analyses.argret;

import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.RegisterFactory.Register;

import chord.analyses.invk.DomI;
import chord.analyses.var.DomV;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (i,z,v) such that local variable v
 * is the zth return variable of method invocation quad i.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "IinvkRet",
    sign = "I0,Z0,V0:I0_V0_Z0"
)
public class RelIinvkRet extends ProgramRel {
    @Override
    public void fill() {
        DomI domI = (DomI) doms[0];
        DomV domV = (DomV) doms[2];
        int numI = domI.size();
        for (int iIdx = 0; iIdx < numI; iIdx++) {
            Quad q = (Quad) domI.get(iIdx);
            RegisterOperand vo = Invoke.getDest(q);
            if (vo != null) {
                Register v = vo.getRegister();
                if (v.getType().isReferenceType()) {
                    int vIdx = domV.indexOf(v);
                    assert (vIdx >= 0);
                    add(iIdx, 0, vIdx);
                }
            }
        }
    }
}
