package chord.analyses.argret;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;

import chord.analyses.method.DomM;
import chord.analyses.var.DomV;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (m,z,v) such that local variable
 * v is the zth argument variable of method m.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "MmethArg",
    sign = "M0,Z0,V0:M0_V0_Z0"
)
public class RelMmethArg extends ProgramRel {
    @Override
    public void fill() {
        DomM domM = (DomM) doms[0];
        DomV domV = (DomV) doms[2];
        int numM = domM.size();
        for (int mIdx = 0; mIdx < numM; mIdx++) {
            jq_Method m = domM.get(mIdx);
            if (m.isAbstract())
                continue;
            ControlFlowGraph cfg = m.getCFG();
            RegisterFactory rf = cfg.getRegisterFactory();
            int numArgs = m.getParamTypes().length;
            for (int zIdx = 0; zIdx < numArgs; zIdx++) {
                Register v = rf.get(zIdx);
                if (v.getType().isReferenceType()) {
                    int vIdx = domV.indexOf(v);
                    assert (vIdx >= 0);
                    add(mIdx, zIdx, vIdx);
                }
            }
        }
    }
}
