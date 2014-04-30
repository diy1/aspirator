package chord.analyses.inst;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.RegisterFactory.Register;

import chord.program.visitors.IMoveInstVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(name = "PobjVarMoveInst", sign = "P0,V0,V1:P0_V0xV1")
public class RelPobjVarMoveInst extends ProgramRel implements IMoveInstVisitor {
    public void visit(jq_Class c) { }
    public void visit(jq_Method m) { }
    public void visitMoveInst(Quad q) {
        Operand rx = Move.getSrc(q);
        if (rx instanceof RegisterOperand) {
            RegisterOperand ro = (RegisterOperand) rx;
            if (ro.getType().isReferenceType()) {
                Register r = ro.getRegister();
                Register l = Move.getDest(q).getRegister();
                add(q, l, r);
            }
        }
    }
}

