package chord.analyses.escape;

import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Operator.Getstatic;
import joeq.Compiler.Quad.Operator.Putstatic;
import joeq.Compiler.Quad.Quad;
import chord.analyses.heapacc.DomE;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramRel;

@Chord(name = "queryE", sign = "E0:E0", consumes = { "checkExcludedE" })
public class RelQueryE extends ProgramRel {
    @Override
    public void fill() {
        ProgramRel relCheckExcludedE = (ProgramRel) ClassicProject.g().getTrgt("checkExcludedE");
        relCheckExcludedE.load();
        DomE domE = (DomE) doms[0];
        for (Quad q : domE) {
            Operator op = q.getOperator();
            if (op instanceof Getstatic || op instanceof Putstatic)
                continue;
            if (!relCheckExcludedE.contains(q))
                add(q);
        }
        relCheckExcludedE.close();
    }
}
