package chord.analyses.inst;

import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operand.FieldOperand;
import joeq.Compiler.Quad.Operator.AStore;
import joeq.Compiler.Quad.Operator.Putfield;
import joeq.Compiler.Quad.RegisterFactory.Register;

import chord.analyses.field.DomF;
import chord.analyses.method.DomM;
import chord.analyses.var.DomV;
import chord.program.visitors.IHeapInstVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (m,b,f,v) such that method m
 * contains a quad of the form <tt>b.f = v</tt>.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "MputInstFldInst",
    sign = "M0,V0,F0,V1:F0_M0_V0xV1"
)
public class RelMputInstFldInst extends ProgramRel implements IHeapInstVisitor {
    private DomM domM;
    private DomV domV;
    private DomF domF;
    private jq_Method ctnrMethod;
    public void init() {
        domM = (DomM) doms[0];
        domV = (DomV) doms[1];
        domF = (DomF) doms[2];
    }
    public void visit(jq_Class c) { }
    public void visit(jq_Method m) {
        ctnrMethod = m;
    }
    public void visitHeapInst(Quad q) {
        Operator op = q.getOperator();
        if (op instanceof AStore) {
            if (((AStore) op).getType().isReferenceType()) {
                Operand rx = AStore.getValue(q);
                if (rx instanceof RegisterOperand) {
                    RegisterOperand ro = (RegisterOperand) rx;
                    Register r = ro.getRegister();
                    RegisterOperand bo = (RegisterOperand) AStore.getBase(q);
                    Register b = bo.getRegister();
                    int mIdx = domM.indexOf(ctnrMethod);
                    assert (mIdx >= 0);
                    int bIdx = domV.indexOf(b);
                    assert (bIdx >= 0);
                    int rIdx = domV.indexOf(r);
                    assert (rIdx >= 0);
                    int fIdx = 0;
                    add(mIdx, bIdx, fIdx, rIdx);
                }
            }
            return;
        }
        if (op instanceof Putfield) {
            jq_Field f = Putfield.getField(q).getField();
            if (f.getType().isReferenceType()) {
                Operand rx = Putfield.getSrc(q);
                if (rx instanceof RegisterOperand) {
                    Operand bx = Putfield.getBase(q);
                    if (bx instanceof RegisterOperand) {
                        RegisterOperand bo = (RegisterOperand) bx;
                        Register b = bo.getRegister();
                        RegisterOperand ro = (RegisterOperand) rx;
                        Register r = ro.getRegister();
                        int mIdx = domM.indexOf(ctnrMethod);
                        assert (mIdx >= 0);
                        int bIdx = domV.indexOf(b);
                        assert (bIdx >= 0);
                        int rIdx = domV.indexOf(r);
                        assert (rIdx >= 0);
                        int fIdx = domF.indexOf(f);
                        assert (fIdx >= 0);
                        add(mIdx, bIdx, fIdx, rIdx);
                    }
                }
            }
        }
    }
}
