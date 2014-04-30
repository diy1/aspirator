package chord.analyses.inst;

import joeq.Class.jq_Type;
import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_Reference;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.Operand.AConstOperand;
import joeq.Compiler.Quad.RegisterFactory.Register;

import chord.program.visitors.IMoveInstVisitor;
import chord.program.Program;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (m,v,t) such that method m
 * contains a statement of the form <tt>v = t.class/tt>.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "MclsValAsgnInst",
    sign = "M0,V0,T0:M0_V0_T0"
)
public class RelMclsValAsgnInst extends ProgramRel
        implements IMoveInstVisitor {
    private jq_Method ctnrMethod;
    public void visit(jq_Class c) { }
    public void visit(jq_Method m) {
        ctnrMethod = m;
    }
    public void visitMoveInst(Quad q) {
        Operand ro = Move.getSrc(q);
        if (ro instanceof AConstOperand) {
            Object c = ((AConstOperand) ro).getValue();
            if (c instanceof Class) {
                String s = ((Class) c).getName();
                // s is in encoded form only if it is an array type
                if (s.startsWith("["))
                    s = Program.typesToStr(s);
                jq_Reference t = Program.g().getClass(s);
                assert t != null : s + "@" + ctnrMethod;
                RegisterOperand lo = Move.getDest(q);
                Register l = lo.getRegister();
                add(ctnrMethod, l, t);
            }
        }
    }
}
