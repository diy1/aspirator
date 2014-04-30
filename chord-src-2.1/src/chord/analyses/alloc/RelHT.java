package chord.analyses.alloc;

import java.util.List;

import joeq.Class.jq_Type;
import joeq.Class.jq_Reference;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.MultiNewArray;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import chord.program.Reflect;
import chord.analyses.alloc.DomH;
import chord.analyses.type.DomT;
import chord.program.Program;
import chord.program.PhantomObjVal;
import chord.program.PhantomClsVal;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;
import chord.util.tuple.object.Pair;
import chord.project.Messages;

/**
 * Relation containing each tuple (h,t) such that object allocation quad h
 * allocates objects of type t.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "HT",
    sign = "H0,T1:T1_H0"
)
public class RelHT extends ProgramRel {
    private DomH domH;
    private DomT domT;

    @Override
    public void fill() {
        domH = (DomH) doms[0];
        domT = (DomT) doms[1];
        int numH = domH.size();
        int numA = domH.getLastA() + 1;
        for (int hIdx = 1; hIdx < numA; hIdx++) {
            Quad h = (Quad) domH.get(hIdx);
            Operator op = h.getOperator();
            jq_Type t;
            // do NOT merge handling of New and NewArray
            if (op instanceof New)
                t = New.getType(h).getType();
            else if (op instanceof NewArray) {
                t = NewArray.getType(h).getType();
            } else if (op instanceof Invoke) {     
                t = Invoke.getDest(h).getType();
            } else if (op instanceof MultiNewArray) {
                t = MultiNewArray.getType(h).getType();
            } else {
                Messages.fatal("ERROR: RelHT: Unexpected quad kind %s in domain H", op);
                t = null;
            } 
            int tIdx = domT.indexOf(t);
            if (tIdx == -1) {
                Messages.log("WARN: RelHT: Cannot find type %s in domain T; " +
                    " referenced by quad %s in method %s", t, h, h.getMethod());
                continue;
            }
            add(hIdx, tIdx);
        }
        Reflect reflect = Program.g().getReflect();
        processResolvedNewInstSites(reflect.getResolvedObjNewInstSites());
        processResolvedNewInstSites(reflect.getResolvedConNewInstSites());
        processResolvedNewInstSites(reflect.getResolvedAryNewInstSites());
    }

    private void processResolvedNewInstSites(List<Pair<Quad, List<jq_Reference>>> l) {
        for (Pair<Quad, List<jq_Reference>> p : l) {
            Quad q = p.val0;
            int hIdx = domH.indexOf(q);
            assert (hIdx >= 0);
            for (jq_Reference t : p.val1) {
                int tIdx = domT.indexOf(t);
                assert (tIdx >= 0);
                add(hIdx, tIdx);
            }
        }
    }
}
