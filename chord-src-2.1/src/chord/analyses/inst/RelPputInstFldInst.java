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
import chord.analyses.point.DomP;
import chord.analyses.var.DomV;
import chord.program.visitors.IHeapInstVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (p,b,f,v) such that the statement
 * at program point p is of the form <tt>b.f = v</tt>.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "PputInstFldInst",
    sign = "P0,V0,F0,V1:F0_P0_V0xV1"
)
public class RelPputInstFldInst extends ProgramRel implements IHeapInstVisitor {
    private DomP domP;
    private DomV domV;
    private DomF domF;
    public void init() {
        domP = (DomP) doms[0];
        domV = (DomV) doms[1];
        domF = (DomF) doms[2];
    }
    public void visit(jq_Class c) { }
    public void visit(jq_Method m) { }
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
                    int pIdx = domP.indexOf(q);
                    assert (pIdx >= 0);
                    int bIdx = domV.indexOf(b);
                    assert (bIdx >= 0);
                    int rIdx = domV.indexOf(r);
                    assert (rIdx >= 0);
                    int fIdx = 0;
                    add(pIdx, bIdx, fIdx, rIdx);
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
                        int pIdx = domP.indexOf(q);
                        assert (pIdx >= 0);
                        int bIdx = domV.indexOf(b);
                        assert (bIdx >= 0);
                        int rIdx = domV.indexOf(r);
                        assert (rIdx >= 0);
                        int fIdx = domF.indexOf(f);
                        assert (fIdx >= 0);
                        add(pIdx, bIdx, fIdx, rIdx);
                    }
                }
            }
        }
    }
}
