package chord.program.visitors;

import joeq.Class.jq_Method;

/**
 * Visitor over all methods of all classes in the program.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public interface IMethodVisitor extends IClassVisitor {
    /**
     * Visits all methods of all classes in the program.
     * 
     * @param m A method.
     */
    public void visit(jq_Method m);
}
