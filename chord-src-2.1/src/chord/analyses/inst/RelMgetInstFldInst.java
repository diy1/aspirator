package chord.analyses.inst;

import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operand.AConstOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.ALoad;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.RegisterFactory.Register;

import chord.analyses.field.DomF;
import chord.analyses.method.DomM;
import chord.analyses.var.DomV;
import chord.program.visitors.IHeapInstVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (m,v,b,f) such that method m
 * contains a quad of the form <tt>v = b.f</tt>.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "MgetInstFldInst",
    sign = "M0,V0,V1,F0:F0_M0_V0xV1"
)
public class RelMgetInstFldInst extends ProgramRel implements IHeapInstVisitor {
    private DomM domM;
    private DomV domV;
    private DomF domF;
    public void init() {
        domM = (DomM) doms[0];
        domV = (DomV) doms[1];
        domF = (DomF) doms[3];
    }
    private jq_Method ctnrMethod;
    public void visit(jq_Class c) { }
    public void visit(jq_Method m) {
        ctnrMethod = m;
    }
    public void visitHeapInst(Quad q) {
        Operator op = q.getOperator();
        if (op instanceof ALoad) {
            if (((ALoad) op).getType().isReferenceType()) {
                RegisterOperand lo = ALoad.getDest(q);
                Register l = lo.getRegister();
                RegisterOperand bo = (RegisterOperand) ALoad.getBase(q);
                Register b = bo.getRegister();
                int mIdx = domM.indexOf(ctnrMethod);
                assert (mIdx >= 0);
                int lIdx = domV.indexOf(l);
                assert (lIdx >= 0);
                int bIdx = domV.indexOf(b);
                assert (bIdx >= 0);
                int fIdx = 0;
                add(mIdx, lIdx, bIdx, fIdx);
            }
            return;
        }
        if (op instanceof Getfield) {
            jq_Field f = Getfield.getField(q).getField();
            if (f.getType().isReferenceType()) {
                Operand bx = Getfield.getBase(q);
                if (bx instanceof RegisterOperand) {
                    RegisterOperand bo = (RegisterOperand) bx;
                    Register b = bo.getRegister();
                    RegisterOperand lo = Getfield.getDest(q);
                    Register l = lo.getRegister();
                    int mIdx = domM.indexOf(ctnrMethod);
                    assert (mIdx >= 0);
                    int bIdx = domV.indexOf(b);
                    assert (bIdx >= 0);
                    int lIdx = domV.indexOf(l);
                    assert (lIdx >= 0);
                    int fIdx = domF.indexOf(f);
                    assert (fIdx >= 0);
                    add(mIdx, lIdx, bIdx, fIdx);
                } else
                    assert (bx instanceof AConstOperand);
            }
        }
    }
}
