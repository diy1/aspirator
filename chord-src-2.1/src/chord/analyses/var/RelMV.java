package chord.analyses.var;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.var.DomV;

/**
 * Relation containing each tuple (m,v) such that method m
 * declares local variable v, that is, v is either an
 * argument or temporary variable of m.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "MV",
    sign = "M0,V0:M0_V0"
)
public class RelMV extends ProgramRel {
    public void fill() {
        DomV domV = (DomV) doms[1];
        for (Register v : domV) {
            jq_Method m = domV.getMethod(v);
            add(m, v);
        }
    }
}
