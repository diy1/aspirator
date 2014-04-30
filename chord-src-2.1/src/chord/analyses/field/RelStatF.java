package chord.analyses.field;

import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import chord.program.visitors.IFieldVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing all static (as opposed to instance) fields.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "statF",
    sign = "F0"
)
public class RelStatF extends ProgramRel implements IFieldVisitor {
    public void visit(jq_Class c) { }
    public void visit(jq_Field f) {
        if (f.isStatic())
            add(f);
    }
}
