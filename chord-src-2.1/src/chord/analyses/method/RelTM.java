package chord.analyses.method;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import chord.program.visitors.IMethodVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (t,m) such that method m is defined in type t.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "TM",
    sign = "T0,M0:M0_T0"
)
public class RelTM extends ProgramRel implements IMethodVisitor {
    private jq_Class ctnrClass;
    public void visit(jq_Class c) {
        ctnrClass = c;
    }
    public void visit(jq_Method m) {
        add(ctnrClass, m);
    }
}
