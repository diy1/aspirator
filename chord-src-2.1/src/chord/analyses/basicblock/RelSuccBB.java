package chord.analyses.basicblock;

import java.util.List;

import joeq.Compiler.Quad.BasicBlock;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;
import chord.analyses.basicblock.DomB;

/**
 * Relation containing each pair of basic blocks (b1,b2)
 * such that b2 is immediate successor of b1. 
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "succBB",
    sign = "B0,B1:B0xB1"
)
public class RelSuccBB extends ProgramRel {
    public void fill() {
        DomB domB = (DomB) doms[0];
        int numB = domB.size();
        for (int bIdx = 0; bIdx < numB; bIdx++) {
            BasicBlock bb = domB.get(bIdx);
            List<BasicBlock> succs = bb.getSuccessors();
            for (BasicBlock bb2 : succs) {
                int bIdx2 = domB.indexOf(bb2);
                assert (bIdx2 >= 0);
                add(bIdx, bIdx2);
            }
        }
    }
}
