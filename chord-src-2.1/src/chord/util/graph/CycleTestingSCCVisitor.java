package chord.util.graph;

/**
 * A visitor over the Strongly Connected Components (SCCs) of a directed graph that checks for cycles in the graph.
 * <p>
 * The visitor visits each SCC of the graph and terminates by throwing an {@link GraphEntityVisitorException} as soon as it detects a cycle, namely,
 * if it visits an SCC containing more than one node or an SCC containing a single node with a self-loop.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class CycleTestingSCCVisitor<Node> implements IGraphEntityVisitor<Node> {
    private final IGraph<Node> graph;
    private Node fstNodeInScc;
    public CycleTestingSCCVisitor(IGraph<Node> graph) {
        this.graph = graph;
    }
    public void prologue() {
        // do nothing
    }
    public void visit(Node node) {
        if (fstNodeInScc != null)
            throw new GraphEntityVisitorException();
        else
            fstNodeInScc = node;
    }
    public void epilogue() {
        Node node = fstNodeInScc;
        if (node != null) {
            if (graph.hasEdge(node, node))
                throw new GraphEntityVisitorException();
            fstNodeInScc = null;
        }
    }
}    
