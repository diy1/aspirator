package chord.analyses.type;

import joeq.Class.jq_Type;
import joeq.Class.jq_Class;
import chord.program.Program;
import chord.project.Chord;
import chord.project.analyses.ProgramDom;
import chord.util.IndexSet;

/**
 * Domain of classes.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "T"
)
public class DomT extends ProgramDom<jq_Type> {
    public void fill() {
        Program program = Program.g();
        IndexSet<jq_Type> types = program.getTypes();
        for (jq_Type t : types)
            add(t);
    }
    public String toXMLAttrsString(jq_Type t) {
        String name = t.getName();
        String file;
        if (t instanceof jq_Class) {
            jq_Class c = (jq_Class) t;
            file = c.getSourceFileName();
        } else
            file = "";
        int line = 0;  // TODO
        return "name=\"" + name +
            "\" file=\"" + file +
            "\" line=\"" + line + "\"";
    }
}
