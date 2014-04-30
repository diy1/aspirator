package chord.analyses.reflect;

import java.util.List;

import joeq.Class.jq_Reference;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.Invoke;

import chord.analyses.invk.DomI;
import chord.analyses.type.DomT;
import chord.program.Program;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;
import chord.util.tuple.object.Pair;

/**
 * Relation containing each tuple (i,t) such that call site i
 * calling method "static Class forName(String className)" defined in
 * class "java.lang.Class" was determined by reflection analysis as
 * potentially loading class t.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "clsForNameIT",
    sign = "I0,T0:I0_T0"
)
public class RelClsForNameIT extends ProgramRel {
    public void fill() {
        DomI domI = (DomI) doms[0];
        DomT domT = (DomT) doms[1];
        List<Pair<Quad, List<jq_Reference>>> l =
            Program.g().getReflect().getResolvedClsForNameSites();
        for (Pair<Quad, List<jq_Reference>> p : l) {
            Quad q = p.val0;
            int iIdx = domI.indexOf(q);
            assert (iIdx >= 0);
            for (jq_Reference t : p.val1) {
                int tIdx = domT.indexOf(t);
                assert (tIdx >= 0);
                add(iIdx, tIdx);
            }
        }
    }
}
