package chord.analyses.heapacc;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Putfield;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import chord.program.visitors.IHeapInstVisitor;
import chord.analyses.method.DomM;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.analyses.ProgramDom;

/**
 * Domain of quads that access (read or write) an instance field,
 * a static field, or an array element.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "E",
    consumes = { "M" }
)
public class DomE extends ProgramDom<Quad> implements IHeapInstVisitor {
    protected DomM domM;
    public void init() {
        domM = (DomM) (Config.classic ?
            ClassicProject.g().getTrgt("M") : consumes[0]);
    }
    public void visit(jq_Class c) { }
    public void visit(jq_Method m) { }
    public void visitHeapInst(Quad q) {
        Operator op = q.getOperator();
        if (op instanceof Getfield) {
            if (!(Getfield.getBase(q) instanceof RegisterOperand))
                return;
        }
        if (op instanceof Putfield) {
            if (!(Putfield.getBase(q) instanceof RegisterOperand))
                return;
        }
        add(q);
    }
    public String toUniqueString(Quad q) {
        return q.toByteLocStr();
    }
    public String toXMLAttrsString(Quad q) {
        Operator op = q.getOperator();
        jq_Method m = q.getMethod();
        String file = m.getDeclaringClass().getSourceFileName();
        int line = q.getLineNumber();
        int mIdx = domM.indexOf(m);
        return "file=\"" + file + "\" " + "line=\"" + line + "\" " +
            "Mid=\"M" + mIdx + "\"" +
            " rdwr=\"" + (op.isWrHeapInst() ? "Wr" : "Rd") + "\"";
    }
}
