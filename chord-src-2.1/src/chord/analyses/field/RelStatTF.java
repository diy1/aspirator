package chord.analyses.field;

import joeq.Class.jq_Field;
import joeq.Class.jq_Class;
import chord.program.visitors.IFieldVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (t,f) such that f is a
 * static field defined in type t.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "staticTF",
    sign = "T0,F0:F0_T0"
)
public class RelStatTF extends ProgramRel implements IFieldVisitor {
    private jq_Class ctnrClass;
    public void visit(jq_Class c) {
        ctnrClass = c;
    }
    public void visit(jq_Field f) {
        if (f.isStatic()) {
            add(ctnrClass, f);
        }
    }
}
