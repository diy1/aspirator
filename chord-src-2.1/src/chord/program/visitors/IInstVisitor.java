package chord.program.visitors;

import joeq.Compiler.Quad.Quad;

/**
 * Visitor over all statements in all methods in the program.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public interface IInstVisitor extends IMethodVisitor {
    /**
     * Visits all statements in all methods in the program.
     * 
     * @param q A statement.
     */
    public void visit(Quad q);
}
