package chord.analyses.type;

import joeq.Class.jq_Reference;
import chord.analyses.type.DomT;
import chord.program.Program;
import chord.project.Chord;
import chord.project.Config;
import chord.project.analyses.ProgramRel;
import chord.util.IndexSet;

/**
 * Relation containing each type t the prefix of whose name
 * is contained in the value of system property
 * <tt>chord.check.exclude</tt>.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "checkExcludedT",
    sign = "T0:T0"
)
public class RelCheckExcludedT extends ProgramRel {
    public void fill() {
        DomT domT = (DomT) doms[0];
        Program program = Program.g();
        IndexSet<jq_Reference> classes = program.getClasses();
        String[] checkExcludeAry = Config.checkExcludeAry;
        for (jq_Reference c : classes) {
            String cName = c.getName();
            for (String prefix : checkExcludeAry) {
                if (cName.startsWith(prefix)) {
                    add(c);
                    break;
                }
            }
        }
    }
}
