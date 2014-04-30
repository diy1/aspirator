package chord.program.visitors;

import joeq.Compiler.Quad.Quad;

/**
 * Visitor over all monitorenter statements in all methods in the program.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public interface IAcqLockInstVisitor extends IMethodVisitor {
    /**
     * Visits all monitorenter statements in all methods in the program.
     * 
     * @param q A monitorenter statement.
     */
    public void visitAcqLockInst(Quad q);
}
