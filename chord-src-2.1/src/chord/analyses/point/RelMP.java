package chord.analyses.point;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import chord.program.visitors.IInstVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (m,p) such that method m contains program point p.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "MP",
    sign = "M0,P0:M0xP0"
)
public class RelMP extends ProgramRel implements IInstVisitor {
    private jq_Method ctnrMethod;
    public void visit(jq_Class c) { }
    public void visit(jq_Method m) {
        ctnrMethod = m;
    }
    public void visit(Quad q) {
        add(ctnrMethod, q);
    }
}
