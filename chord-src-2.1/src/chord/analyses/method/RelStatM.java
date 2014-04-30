package chord.analyses.method;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import chord.program.visitors.IMethodVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing all static (as opposed to instance) methods.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "statM",
    sign = "M0"
)
public class RelStatM extends ProgramRel implements IMethodVisitor {
    public void visit(jq_Class c) { }
    public void visit(jq_Method m) {
        if (m.isStatic())
            add(m);
    }
}
