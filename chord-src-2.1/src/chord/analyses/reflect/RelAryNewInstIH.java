package chord.analyses.reflect;

import java.util.List;

import joeq.Class.jq_Method;
import joeq.Class.jq_Reference;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.Invoke;

import chord.analyses.alloc.DomH;
import chord.analyses.invk.DomI;
import chord.program.Program;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;
import chord.util.tuple.object.Pair;

/**
 * Relation containing each tuple (i,h) such that call site i
 * calling method "static Object newInstance(Class componentType, int length)"
 * defined in class "java.lang.reflect.Array" is treated as
 * object allocation site h.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "aryNewInstIH",
    sign = "I0,H0:I0_H0"
)
public class RelAryNewInstIH extends ProgramRel {
    public void fill() {
        DomI domI = (DomI) doms[0];
        DomH domH = (DomH) doms[1];
        List<Pair<Quad, List<jq_Reference>>> l =
            Program.g().getReflect().getResolvedAryNewInstSites();
        for (Pair<Quad, List<jq_Reference>> p : l) {
            Quad q = p.val0;
            int iIdx = domI.indexOf(q);
            assert (iIdx >= 0);
            int hIdx = domH.indexOf(q);
            assert (hIdx >= 0);
            add(iIdx, hIdx);
        }
    }
}

