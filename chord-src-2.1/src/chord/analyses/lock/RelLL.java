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
import chord.program.visitors.IMethodVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each tuple (l1,l2) such that the synchronized
 * block or synchronized method that acquires the lock at point l1
 * lexically encloses (directly or transitively) the synchronized block
 * that acquires the lock at point l2.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "LL",
    sign = "L0,L1:L0xL1"
)
public class RelLL extends ProgramRel implements IMethodVisitor {
    private Set<BasicBlock> visited = new HashSet<BasicBlock>();
    private DomL domL;
    public void init() {
        domL = (DomL) doms[0];
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
                    int lIdx = domL.indexOf(q);
                    assert (lIdx >= 0);
                    TIntArrayList locks2 = new TIntArrayList(k + 1);
                    if (k > 0) {
                        int lIdx2 = locks.get(k - 1);
                        for (int j = 0; j < k - 1; j++)
                            locks2.add(locks.get(j));
                        locks2.add(lIdx2);
                        add(lIdx2, lIdx);
                    }
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
