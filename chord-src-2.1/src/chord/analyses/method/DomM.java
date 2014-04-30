package chord.analyses.method;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import chord.program.Program;
import chord.program.visitors.IMethodVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramDom;

/**
 * Domain of methods.
 * <p>
 * The 0th element in this domain is the main method of the program.
 * <p>
 * The 1st element in this domain is the <tt>start()</tt> method
 * of class <tt>java.lang.Thread</tt>, if this method is reachable
 * from the main method of the program.
 * <p>
 * The above two methods are the entry-point methods of the implicitly
 * created main thread and each explicitly created thread,
 * respectively.  Due to Chord's emphasis on concurrency, these
 * methods are referenced frequently by various pre-defined program
 * analyses expressed in Datalog, and giving them special indices
 * makes it convenient to reference them in those analyses.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "M"
)
public class DomM extends ProgramDom<jq_Method> implements IMethodVisitor {
    public void init() {
        // Reserve index 0 for the main method of the program.
        // Reserve index 1 for the start() method of java.lang.Thread
        // if it exists.
        Program program = Program.g();
        jq_Method mainMethod = program.getMainMethod();
        assert (mainMethod != null);
        getOrAdd(mainMethod);
        jq_Method startMethod = program.getThreadStartMethod();
        if (startMethod != null)
            getOrAdd(startMethod);
    }
    public void visit(jq_Class c) { }
    public void visit(jq_Method m) {
        getOrAdd(m);
    }
    public String toXMLAttrsString(jq_Method m) {
        jq_Class c = m.getDeclaringClass();
        String methName = m.getName().toString();
        String sign = c.getName() + ".";
        if (methName.equals("<init>")) {
            sign += "&lt;init&gt;";
            System.out.println("Ding: init found" + methName);
        }
        else if (methName.equals("<clinit>"))
            sign += "&lt;clinit&gt;";
        else
            sign += methName;
        String desc = m.getDesc().toString();
        String args = desc.substring(1, desc.indexOf(')'));
        sign += "(" + Program.typesToStr(args) + ")";
        String file = c.getSourceFileName();
        int line = m.getLineNumber(0);  // TODO
        return "sign=\"" + sign +
            "\" file=\"" + file +
            "\" line=\"" + line + "\"";
    }
}
