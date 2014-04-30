package chord.analyses.datarace;

import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation denoting whether races on accesses in constructor methods must be checked.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "excludeInitMethods",
    sign = "K0:K0"
)
public class RelExcludeInitMethods extends ProgramRel {
    public void fill() {
        if (System.getProperty("chord.datarace.exclude.init", "true").equals("true"))
            add(1);
    }
}
