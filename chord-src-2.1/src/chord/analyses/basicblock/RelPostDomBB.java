package chord.analyses.basicblock;

import chord.util.ArraySet;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Class.jq_Class;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import chord.program.visitors.IMethodVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each pair of basic blocks (b1,b2)
 * such that b1 is immediate postdominator of b2.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "postDomBB",
    sign = "B0,B1:B0xB1"
)
public class RelPostDomBB extends ProgramRel implements IMethodVisitor {
    private final Map<BasicBlock, Set<BasicBlock>> pdomMap =
        new HashMap<BasicBlock, Set<BasicBlock>>();
    public void visit(jq_Class c) { }
    public void visit(jq_Method m) {
        if (m.isAbstract())
            return;
        pdomMap.clear();
        ControlFlowGraph cfg = m.getCFG();
        BasicBlock exit = cfg.exit();
        Set<BasicBlock> exitSet = new ArraySet<BasicBlock>(1);
        exitSet.add(exit);
        pdomMap.put(exit, exitSet);
        List<BasicBlock> rpo = cfg.reversePostOrder();
        int n = rpo.size();
        Set<BasicBlock> initSet = new ArraySet<BasicBlock>(n);
        for (int i = 0; i < n; i++) {
            BasicBlock bb = rpo.get(i);
            initSet.add(bb);
        }
        for (int i = 0; i < n; i++) {
            BasicBlock bb = rpo.get(i);
            if (bb != exit)
                pdomMap.put(bb, initSet);
        }
        boolean changed;
        while (true) {
            changed = false;
            for (int i = n - 1; i >= 0; i--) {
                BasicBlock bb = rpo.get(i);
                if (bb == exit)
                    continue;
                Set<BasicBlock> oldPdom = pdomMap.get(bb);
                Set<BasicBlock> newPdom = null;
                java.util.List<BasicBlock> succs = bb.getSuccessors();
                int k = succs.size();
                if (k >= 1) {
                    Set<BasicBlock> fst = pdomMap.get(succs.get(0));
                    newPdom = new ArraySet<BasicBlock>(fst);
                    for (int j = 1; j < k; j++) {
                        Set<BasicBlock> nxt = pdomMap.get(succs.get(j));
                        newPdom.retainAll(nxt);
                    }
                } else
                    newPdom = new ArraySet<BasicBlock>(1);
                newPdom.add(bb);
                if (!oldPdom.equals(newPdom)) {
                    changed = true;
                    pdomMap.put(bb, newPdom);
                }
            }
            if (!changed)
                break;
        }
        for (BasicBlock bb : pdomMap.keySet()) {
            // System.out.print("postdominators of " + bb + ":");
            for (BasicBlock bb2 : pdomMap.get(bb)) {
                // System.out.print(" " + bb2);
                add(bb2, bb);
            }
            // System.out.println();
        }
    }
}
