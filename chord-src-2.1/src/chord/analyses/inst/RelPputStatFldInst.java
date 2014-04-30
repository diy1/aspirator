package chord.analyses.inst;

import joeq.Class.jq_Method;
import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Operator.Putstatic;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operand.FieldOperand;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.field.DomF;
import chord.analyses.point.DomP;
import chord.analyses.var.DomV;
import chord.program.visitors.IHeapInstVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (p,f,v) such that the quad
 * at program point p is of the form <tt>f = v</tt>.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "PputStatFldInst",
    sign = "P0,F0,V0:F0_P0_V0"
)
public class RelPputStatFldInst extends ProgramRel implements IHeapInstVisitor {
    private DomP domP;
    private DomF domF;
    private DomV domV;
    public void init() {
        domP = (DomP) doms[0];
        domF = (DomF) doms[1];
        domV = (DomV) doms[2];
    }
    public void visit(jq_Class c) { }
    public void visit(jq_Method m) { }
    public void visitHeapInst(Quad q) {
        Operator op = q.getOperator();
        if (op instanceof Putstatic) {
            jq_Field f = Putstatic.getField(q).getField();
            if (f.getType().isReferenceType()) {
                Operand rx = Putstatic.getSrc(q);
                if (rx instanceof RegisterOperand) {
                    RegisterOperand ro = (RegisterOperand) rx;
                    Register r = ro.getRegister();
                    int pIdx = domP.indexOf(q);
                    assert (pIdx >= 0);
                    int rIdx = domV.indexOf(r);
                    assert (rIdx >= 0);
                    int fIdx = domF.indexOf(f);
                    assert (fIdx >= 0);
                    add(pIdx, fIdx, rIdx);
                }
            }
        }
    }
}
