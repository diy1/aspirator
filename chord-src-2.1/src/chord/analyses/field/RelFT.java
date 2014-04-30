package chord.analyses.field;

import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_Type;
import chord.program.visitors.IFieldVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (f,t) such that field f has type t.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "FT",
    sign = "F0,T0:T0_F0"
)
public class RelFT extends ProgramRel implements IFieldVisitor {
    public void visit(jq_Class c) { }
    public void visit(jq_Field f) {
        jq_Type t = f.getType();
        add(f, t);
    }
}
