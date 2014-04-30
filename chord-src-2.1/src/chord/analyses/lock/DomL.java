package chord.analyses.lock;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.EntryOrExitBasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Quad;

import chord.program.visitors.IAcqLockInstVisitor;
import chord.analyses.method.DomM;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.analyses.ProgramDom;

/**
 * Domain of all lock acquire points, including monitorenter
 * quads and entry basic blocks of synchronized methods.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "L",
    consumes = { "M" }
)
public class DomL extends ProgramDom<Inst> implements IAcqLockInstVisitor {
    protected DomM domM;
    protected jq_Method ctnrMethod;
    public void init() {
        domM = (DomM) (Config.classic ?
            ClassicProject.g().getTrgt("M") : consumes[0]);
    }
    public void visit(jq_Class c) { }
    public void visit(jq_Method m) {
        if (m.isAbstract())
            return;
        if (m.isSynchronized()) {
            ControlFlowGraph cfg = m.getCFG();
            EntryOrExitBasicBlock head = cfg.entry();
            add(head);
        }
    }
    public void visitAcqLockInst(Quad q) {
        add(q);
    }
    public String toUniqueString(Inst i) {
        return i.toByteLocStr();
    }
    public String toXMLAttrsString(Inst i) {
        jq_Method m = i.getMethod();
        String file = m.getDeclaringClass().getSourceFileName();
        int line = i.getLineNumber();
        int mIdx = domM.indexOf(m);
        return "file=\"" + file + "\" " + "line=\"" + line + "\" " +
            "Mid=\"M" + mIdx + "\"";
    }
}
