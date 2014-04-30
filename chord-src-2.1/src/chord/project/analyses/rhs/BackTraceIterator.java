package chord.project.analyses.rhs;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Stack;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.EntryOrExitBasicBlock;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Inst;

/**
 * The backward trace iterator.
 * 
 * @author xin
 */
public class BackTraceIterator<PE extends IEdge, SE extends IEdge> implements Iterator<IWrappedPE<PE, SE>> {
    private IWrappedPE<PE, SE> currentWPE;                // current wrapped path edge
    private final Stack<IWrappedPE<PE, SE>> callStack;    // call stack for dealing with summary edges
    private Set<jq_Method> skipList;

    /**
     * Instantiate a {@link BackTraceIterator}.
     * 
     * @param wpe The wrapped path edge as the iterator's first element
     */
    public BackTraceIterator(final IWrappedPE<PE, SE> wpe) {
        currentWPE = wpe;
        callStack = new Stack<IWrappedPE<PE, SE>>();
        skipList = new HashSet<jq_Method>();
    }

    public void addMethodToSkipList(jq_Method m) {
        this.skipList.add(m);
    }
    
    @Override
    public boolean hasNext() {
        return (currentWPE != null);
    }

    @Override
    public IWrappedPE<PE, SE> next() {
        IWrappedPE<PE, SE> ret = currentWPE;
        Inst inst = currentWPE.getInst();
        if (inst instanceof EntryOrExitBasicBlock) {
            EntryOrExitBasicBlock bb = (EntryOrExitBasicBlock) inst;
            if (bb.isEntry() && !callStack.empty()) {
                currentWPE = callStack.pop();
                return ret;
            }
        }
        IWrappedSE<PE, SE> wse = currentWPE.getWSE();
        IWrappedPE<PE, SE> wpe = currentWPE.getWPE();
        if (wse != null && !skipList.contains(wse.getWPE().getInst().getMethod())) {
            Quad q = (Quad) wpe.getInst();
            if (!(q.getOperator() instanceof Invoke)) {
                throw new RuntimeException("Provence must be an invoke instruction!");
            }
            callStack.push(wpe);
            currentWPE = wse.getWPE();
        } else {
            currentWPE = wpe;
        }
        return ret;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Remove operation not supported!");
    }

    /**
     * Provides the current wrapped path edge.
     * 
     * @return  The current wrapped path edge.
     */
    public IWrappedPE<PE, SE> curr() {
        return currentWPE;
    }
}
