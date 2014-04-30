package chord.analyses.field;

import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import chord.program.visitors.IFieldVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing all instance (as opposed to static) fields.
 * It does not include the distinguished hypothetical field
 * <tt>arrayElem</tt> that is regarded as accessed whenever an
 * array element is read/written.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "instF",
    sign = "F0"
)
public class RelInstF extends ProgramRel implements IFieldVisitor {
    public void visit(jq_Class c) { }
    public void visit(jq_Field f) { 
        if (!f.isStatic())
            add(f);
    }
}
