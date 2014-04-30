package chord.analyses.method;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import chord.program.visitors.IMethodVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (t,m) such that m is a
 * static method defined in type t.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "staticTM",
    sign = "T0,M0:M0_T0"
)
public class RelStatTM extends ProgramRel implements IMethodVisitor {
    public void visit(jq_Class c) { }
    public void visit(jq_Method m) {
        if (m.isStatic()) {
            jq_Class t = m.getDeclaringClass();
            add(t, m);
        }
    }
}
