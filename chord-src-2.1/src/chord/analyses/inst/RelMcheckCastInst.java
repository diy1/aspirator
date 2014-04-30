package chord.analyses.inst;

import joeq.Class.jq_Class;
import joeq.Class.jq_Reference;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.CheckCast;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.method.DomM;
import chord.analyses.type.DomT;
import chord.analyses.var.DomV;
import chord.program.visitors.IMoveInstVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;
import chord.program.visitors.ICastInstVisitor;

/**
 * Relation containing each tuple (m,v1,t,v2) such that method m
 * contains a statement of the form <tt>v1 = (t) v2</tt>.
 *
 * @author Omer Tripp (omertrip@post.tau.ac.il)
 */
@Chord(
    name = "McheckCastInst",
    sign = "M0,V0,T0,V1:M0_T0_V0xV1"
)
public class RelMcheckCastInst extends ProgramRel
        implements ICastInstVisitor {
    private jq_Method ctnrMethod;
    private DomM domM;
    private DomV domV;
    private DomT domT;
    public void init() {
        domM = (DomM) doms[0];
        domV = (DomV) doms[1];
        domT = (DomT) doms[2];
    }
    public void visit(jq_Class c) { }
    public void visit(jq_Method m) {
        ctnrMethod = m;
    }
    public void visitCastInst(Quad q) {
        Operand rx = CheckCast.getSrc(q);
        if (rx instanceof RegisterOperand) {
            jq_Type t = CheckCast.getType(q).getType();
            if (t instanceof jq_Reference) {
                RegisterOperand ro = (RegisterOperand) rx;
                Register r = ro.getRegister();
                RegisterOperand lo = CheckCast.getDest(q);
                Register l = lo.getRegister();
                int mIdx = domM.indexOf(ctnrMethod);
                assert (mIdx >= 0);
                int lIdx = domV.indexOf(l);
                assert (lIdx >= 0);
                int tIdx = domT.indexOf(t);
                assert (tIdx >= 0);
                int rIdx = domV.indexOf(r);
                assert (rIdx >= 0);
                add(mIdx, lIdx, tIdx, rIdx);
            }
        }
    }
}
