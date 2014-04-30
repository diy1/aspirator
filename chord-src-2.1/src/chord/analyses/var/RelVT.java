package chord.analyses.var;

import chord.program.Program;
import chord.program.visitors.IMethodVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.RegisterFactory.Register;
import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Class.jq_Reference;

/**
 * Relation containing each tuple (v,t) such that local variable v of reference type has type t.
 * If SSA is used (system property {@code chord.ssa} is set to true) then it is guaranteed that
 * each local variable v has a unique type t.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "VT",
    sign = "V0,T0:T0_V0"
)
public class RelVT extends ProgramRel implements IMethodVisitor {
    private jq_Reference javaLangObject;
    public void init() {
        javaLangObject = Program.g().getClass("java.lang.Object");
        assert (javaLangObject != null);
    }
    public void visit(jq_Class c) { }
    public void visit(jq_Method m) {
        if (m.isAbstract())
            return;
        ControlFlowGraph cfg = m.getCFG();
        RegisterFactory rf = cfg.getRegisterFactory();
        jq_Type[] paramTypes = m.getParamTypes();
        int numArgs = paramTypes.length;
        for (int i = 0; i < numArgs; i++) {
            jq_Type t = paramTypes[i];
            if (t.isReferenceType()) {
                Register v = rf.get(i);
                add(v, t);
            }
        }
        for (BasicBlock bb : cfg.reversePostOrder()) {
            for (Quad q : bb.getQuads()) {
                process(q.getOp1());
                process(q.getOp2());
                process(q.getOp3());
                process(q.getOp4());
            }
        }
    }
    private void process(Operand op) {
        if (op instanceof RegisterOperand) {
            RegisterOperand ro = (RegisterOperand) op;
            jq_Type t = ro.getType();
            if (t == null)
                t = javaLangObject;
            if (t.isReferenceType()) {
                Register v = ro.getRegister();
                add(v, t);
            }
        } else if (op instanceof ParamListOperand) {
            ParamListOperand ros = (ParamListOperand) op;
            int n = ros.length();
            for (int i = 0; i < n; i++) {
                RegisterOperand ro = ros.get(i);
                if (ro == null)
                    continue;
                jq_Type t = ro.getType();
                if (t == null)
                    t = javaLangObject;
                if (t.isReferenceType()) {
                    Register v = ro.getRegister();
                    add(v, t);
                }
            }            
        }
    }
}

