package chord.analyses.inst;

import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operand.FieldOperand;
import joeq.Compiler.Quad.Operator.Putstatic;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.field.DomF;
import chord.analyses.method.DomM;
import chord.analyses.var.DomV;
import chord.program.visitors.IHeapInstVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (m,f,v) such that method m contains
 * a quad of the form <tt>f = v</tt>.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "MputStatFldInst",
    sign = "M0,F0,V0:F0_M0_V0"
)
public class RelMputStatFldInst extends ProgramRel implements IHeapInstVisitor {
    private DomM domM;
    private DomF domF;
    private DomV domV;
    private jq_Method ctnrMethod;
    public void init() {
        domM = (DomM) doms[0];
        domF = (DomF) doms[1];
        domV = (DomV) doms[2];
    }
    public void visit(jq_Class c) { }
    public void visit(jq_Method m) {
        ctnrMethod = m;
    }
    public void visitHeapInst(Quad q) {
        Operator op = q.getOperator();
        if (op instanceof Putstatic) {
            jq_Field f = Putstatic.getField(q).getField();
            if (f.getType().isReferenceType()) {
                Operand rx = Putstatic.getSrc(q);
                if (rx instanceof RegisterOperand) {
                    RegisterOperand ro = (RegisterOperand) rx;
                    Register r = ro.getRegister();
                    int mIdx = domM.indexOf(ctnrMethod);
                    assert (mIdx >= 0);
                    int rIdx = domV.indexOf(r);
                    assert (rIdx >= 0);
                    int fIdx = domF.indexOf(f);
                    assert (fIdx >= 0);
                    add(mIdx, fIdx, rIdx);
                }
            }
        }
    }
}
