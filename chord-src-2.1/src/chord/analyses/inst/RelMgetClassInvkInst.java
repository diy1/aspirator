package chord.analyses.inst;

import java.util.List;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operand.RegisterOperand;

import chord.analyses.invk.DomI;
import chord.analyses.method.DomM;
import chord.program.visitors.IInvokeInstVisitor;
import chord.program.Program;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (m,i) such that method m contains a
 * statement i invoking instance method getClass() defined in class
 * java.lang.Object.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "MgetClassInvkInst",
    sign = "M0,I0:M0xI0"
)
public class RelMgetClassInvkInst extends ProgramRel
        implements IInvokeInstVisitor {
    private DomM domM;
    private DomI domI;
    private jq_Method ctnrMethod;
    public void init() {
        domM = (DomM) doms[0];
        domI = (DomI) doms[1];
    }
    public void visit(jq_Class c) { }
    public void visit(jq_Method m) {
        ctnrMethod = m;
    }
    public void visitInvokeInst(Quad q) {
        jq_Method meth = Invoke.getMethod(q).getMethod();
        if (meth.getName().toString().equals("getClass") &&
            meth.getDesc().toString().equals("()Ljava/lang/Class;") &&
            meth.getDeclaringClass().getName().equals("java.lang.Object")) {
            int mIdx = domM.indexOf(ctnrMethod);
            assert (mIdx >= 0);
            int iIdx = domI.indexOf(q);
            assert (iIdx >= 0);
            add(mIdx, iIdx);
        }
    }
}
