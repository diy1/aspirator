package chord.program.visitors;

import joeq.Class.jq_Class;

/**
 * Visitor over all classes in the program.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public interface IClassVisitor {
    /**
     * Visits all classes in the program.
     *
     * @param c A class.
     */
    public void visit(jq_Class c);
}
