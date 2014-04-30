package chord.analyses.point;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.EntryOrExitBasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import chord.program.visitors.IMethodVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (m,p) such that p is the unique exit basic block of method m.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "MPtail",
    sign = "M0,P0:M0xP0"
)
public class RelMPtail extends ProgramRel
        implements IMethodVisitor {
    public void visit(jq_Class c) { }
    public void visit(jq_Method m) {
        if (m.isAbstract())
            return;
        ControlFlowGraph cfg = m.getCFG();
        EntryOrExitBasicBlock bx = cfg.exit();
        add(m, bx);
    }
}
