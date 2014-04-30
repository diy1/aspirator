package chord.analyses.point;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.EntryOrExitBasicBlock;
import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import chord.analyses.method.DomM;

import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.analyses.ProgramDom;

/**
 * Domain of quads.
 * <p>
 * The 0th element in this domain is the unique entry basic block
 * of the main method of the program.
 * <p>
 * The quads of each method in the program are assigned contiguous
 * indices in this domain, with the unique basic blocks at the
 * entry and exit of each method being assigned the smallest and
 * largest indices, respectively, of all indices assigned to quads
 * in that method.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "P",
    consumes = { "M" }
)
public class DomP extends ProgramDom<Inst> {
    protected DomM domM;
    public void fill() {
        domM = (DomM) (Config.classic ?
            ClassicProject.g().getTrgt("M") : consumes[0]);
        int numM = domM.size();
        for (int mIdx = 0; mIdx < numM; mIdx++) {
            jq_Method m = domM.get(mIdx);
            if (m.isAbstract())
                continue;
            ControlFlowGraph cfg = m.getCFG();
            for (BasicBlock bb : cfg.reversePostOrder()) {
                int n = bb.size();
                if (n == 0) {
                    assert (bb.isEntry() || bb.isExit());
                    add((Inst) bb);
                    continue;
                }
                for (Quad q : bb.getQuads())
                    add(q);
            }
        }
    }
    public String toUniqueString(Inst i) {
        int x;
        if (i instanceof Quad) {
            x = ((Quad) i).getID();
        } else {
            BasicBlock bb = (BasicBlock) i;
            if (bb.isEntry())
                x = -1;
            else if (bb.isExit())
                x = -2;
            else {
                return "null:" + i;
            }
        }
        return x + "!" + i.getMethod();
    }
    
    public String toXMLAttrsString(Inst q) {
        jq_Method m = q.getMethod();
        String file = m.getDeclaringClass().getSourceFileName();
        int line = q.getLineNumber();
        int mIdx = domM.indexOf(m);
        return "file=\"" + file + "\" " + "line=\"" + line + "\" " +
            "Mid=\"M" + mIdx + "\"";
    }
}
