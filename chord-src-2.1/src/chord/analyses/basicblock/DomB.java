package chord.analyses.basicblock;

import java.util.Map;
import java.util.HashMap;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import chord.program.visitors.IMethodVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramDom;

/**
 * Domain of basic blocks.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "B"
)
public class DomB extends ProgramDom<BasicBlock> implements IMethodVisitor {
    protected Map<BasicBlock, jq_Method> basicBlockToMethodMap;
    public void init() {
        basicBlockToMethodMap = new HashMap<BasicBlock, jq_Method>();
    }
    public void visit(jq_Class c) { }
    public void visit(jq_Method m) {
        if (m.isAbstract())
            return;
        ControlFlowGraph cfg = m.getCFG();
        for (BasicBlock b : cfg.reversePostOrder()) {
            basicBlockToMethodMap.put(b, m);
            getOrAdd(b);
        }
    }
    public jq_Method getMethod(BasicBlock b) {
        return basicBlockToMethodMap.get(b);
    }
    public String toUniqueString(BasicBlock b) {
        return b.getID() + "!" + getMethod(b);
    }
}
