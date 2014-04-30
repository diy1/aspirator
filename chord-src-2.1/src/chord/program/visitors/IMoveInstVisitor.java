package chord.program.visitors;

import joeq.Compiler.Quad.Quad;

/**
 * Visitor over all copy assignment statements in all methods in the program.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public interface IMoveInstVisitor extends IMethodVisitor {
    /**
     * Visits all copy assignment statements in all methods in the program.
     * 
     * @param q A copy assignment statement.
     */
    public void visitMoveInst(Quad q);
}
