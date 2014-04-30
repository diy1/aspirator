package chord.program.visitors;

import joeq.Compiler.Quad.Quad;

/**
 * Visitor over all heap accessing statements in all methods in the program.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public interface IHeapInstVisitor extends IMethodVisitor {
    /**
     * Visits all heap accessing statements in all methods in the program.
     * 
     * @param q A heap accessing statement.
     */
    public void visitHeapInst(Quad q);
}
