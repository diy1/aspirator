package chord.analyses.type;

import joeq.Class.jq_Class;
import chord.program.visitors.IClassVisitor;
import chord.project.Chord;
import chord.project.Config;
import chord.project.analyses.ProgramRel;
import chord.util.Utils;

@Chord(
        name = "scopeExcludedT",
        sign = "T0"
    )
public class RelScopeExcludedT extends ProgramRel implements IClassVisitor {
    public void visit(jq_Class c) {
        if (isExcluded(c.getName()))
            add(c);
    }
    
    public static boolean isExcluded(String n) {
        return Utils.prefixMatch(n, Config.scopeExcludeAry);
    }

}
