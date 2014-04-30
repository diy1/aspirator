package chord.analyses.invk;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.Invoke.InvokeStatic;
import chord.analyses.method.DomM;
import chord.project.Messages;
import chord.project.Chord;
import chord.project.Config;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (i,m) such that m is the resolved method of
 * method invocation quad i of kind {@code INVK_STATIC}.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "statIM",
    sign = "I0,M0:I0xM0"
)
public class RelStatIM extends ProgramRel {
    private static final String NOT_FOUND =
        "WARN: RelStatIM: Target %s of call site %s not in domain M; skipping.";
    public void fill() {
        DomI domI = (DomI) doms[0];
        DomM domM = (DomM) doms[1];
        int numI = domI.size();
        for (int iIdx = 0; iIdx < numI; iIdx++) {
            Quad i = (Quad) domI.get(iIdx);
            Operator op = i.getOperator();
            if (op instanceof InvokeStatic) {
                jq_Method m = InvokeStatic.getMethod(i).getMethod();
                if (m.isStatic()) {
                    m = StubRewrite.maybeReplaceCallDest(i.getMethod(), m);
                    int mIdx = domM.indexOf(m);
                    if (mIdx >= 0)
                        add(iIdx, mIdx);
                    else if (Config.verbose >= 2)
                        Messages.log(NOT_FOUND, m, i.toLocStr());
                }
            }
        }
    }
    

}
