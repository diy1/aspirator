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
 * Relation containing each tuple (m,v) such that local variable
 * v is the implicit this argument variable of instance method m.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "thisMV",
    sign = "M0,V0:M0_V0"
)
public class RelThisMV extends ProgramRel {
    @Override
    public void fill() {
        DomM domM = (DomM) doms[0];
        DomV domV = (DomV) doms[1];
        int numM = domM.size();
        for (int mIdx = 0; mIdx < numM; mIdx++) {
            jq_Method m = domM.get(mIdx);
            if (m.isAbstract() || m.isStatic())
                continue;
            ControlFlowGraph cfg = m.getCFG();
            RegisterFactory rf = cfg.getRegisterFactory();
            Register v = rf.get(0); // Ding: heuristic: the register at index 0 is the "this" argument.
            int vIdx = domV.indexOf(v);
            assert (vIdx >= 0);
            add(mIdx, vIdx);
        }
    }
}
