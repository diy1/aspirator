package chord.analyses.invk;

import java.util.List;

import joeq.Class.*;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Operand.MethodOperand;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.Invoke.InvokeStatic;
import chord.analyses.method.DomM;
import chord.program.Program;
import chord.program.Reflect;
import chord.project.Messages;
import chord.project.Chord;
import chord.project.Config;
import chord.project.analyses.ProgramRel;
import chord.util.tuple.object.Pair;

/**
 * Relation containing each tuple (i,m) such that m is the resolved method of
 * method invocation quad i of kind {@code INVK_SPECIAL}.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "specIM",
    sign = "I0,M0:I0xM0"
)
public class RelSpecIM extends ProgramRel {
    private static final String NOT_FOUND =
        "WARN: RelSpecIM: Target  %s of call site %s not in domain M; skipping.";
    public void fill() {
        DomI domI = (DomI) doms[0];
        DomM domM = (DomM) doms[1];
        int numI = domI.size();
        for (int iIdx = 0; iIdx < numI; iIdx++) {
            Quad i = (Quad) domI.get(iIdx);
            Operator op = i.getOperator();
            if (op instanceof InvokeStatic) {
                jq_Method m = InvokeStatic.getMethod(i).getMethod();
                if (!m.isStatic()) {
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
