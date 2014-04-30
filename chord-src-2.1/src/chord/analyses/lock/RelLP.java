package chord.analyses.lock;

import gnu.trove.list.array.TIntArrayList;

import java.util.HashSet;
import java.util.Set;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.Monitor;
import joeq.Compiler.Quad.Operator.Monitor.MONITORENTER;
import chord.analyses.point.DomP;
import chord.program.visitors.IMethodVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (l,p) such that quad p is lexically enclosed in the
 * synchronized block or synchronized method that acquires the lock at point l.
 * <p>
 * A quad may be lexically enclosed in multiple synchronized blocks but in at most one
 * synchronized method (i.e. its containing method).
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "LP",
    sign = "L0,P0:L0_P0"
)
public class RelLP extends ProgramRel implements IMethodVisitor {
    private Set<BasicBlock> visited = new HashSet<BasicBlock>();
    private DomP domP;
    private DomL domL;
    public void init() {
        domL = (DomL) doms[0];
        domP = (DomP) doms[1];
    }
    public void visit(jq_Class c) { }
    public void visit(jq_Method m) {
        if (m.isAbstract())
            return;
        ControlFlowGraph cfg = m.getCFG();
        BasicBlock entry = cfg.entry();
        TIntArrayList locks = new TIntArrayList();
        if (m.isSynchronized()) {
            int lIdx = domL.indexOf(entry);
            assert (lIdx >= 0);
            locks.add(lIdx);
        }
        process(entry, locks);
        visited.clear();
    }
    private void process(BasicBlock bb, TIntArrayList locks) {
        int n = bb.size();
        int k = locks.size();
        for (int i = 0; i < n; i++) {
            Quad q = bb.getQuad(i);
            Operator op = q.getOperator();
            if (op instanceof Monitor) {
                if (op instanceof MONITORENTER) {
                    TIntArrayList locks2 = new TIntArrayList(k + 1);
                    for (int j = 0; j < k; j++)
                        locks2.add(locks.get(j));
                    int lIdx = domL.indexOf(q);
                    assert (lIdx >= 0);
                    locks2.add(lIdx);
                    locks = locks2;
                    k++;
                } else {
                    k--;
                    TIntArrayList locks2 = new TIntArrayList(k);
                    for (int j = 0; j < k; j++)
                        locks2.add(locks.get(j));
                    locks = locks2;
                }
            } else if (k > 0) {
                int pIdx = domP.indexOf(q);
                assert (pIdx >= 0);
                add(locks.get(k - 1), pIdx);
            }
        }
        for (Object o : bb.getSuccessors()) {
            BasicBlock bb2 = (BasicBlock) o;
            if (!visited.contains(bb2)) {
                visited.add(bb2);
                process(bb2, locks);
            }
        }
    }
}
