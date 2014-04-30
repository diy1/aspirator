package chord.util.graph;

import java.util.Set;
import java.util.HashSet;

/**
 * A visitor over the Strongly Connected Components (SCCs) of a directed graph that finds all nodes in cycles.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class CycleFindingSCCVisitor<Node> implements IGraphEntityVisitor<Node> {
    private final IGraph<Node> graph;
    private final Set<Node> nodesInCycles;
    private boolean nonSingleScc;
    private Node fstNodeInScc;
    public CycleFindingSCCVisitor(IGraph<Node> graph) {
        this.graph = graph;
        nodesInCycles = new HashSet<Node>();
    }
    public void prologue() {
        // do nothing
    }
    public void visit(Node node) {
        if (nonSingleScc) {
            nodesInCycles.add(node);
        } else if (fstNodeInScc != null) {
            nodesInCycles.add(node);
            nonSingleScc = true;
        } else
            fstNodeInScc = node;
    }
    public void epilogue() {
        if (nonSingleScc) {
            nodesInCycles.add(fstNodeInScc);
            nonSingleScc = false;
        } else {
            Node node = fstNodeInScc;
            if (graph.hasEdge(node, node)) {
                // singleton scc with self loop
                nodesInCycles.add(node);
            }
        }
        fstNodeInScc = null;
    }
    /**
     * Provides all nodes in cycles in the directed graph.
     *
     * @return All nodes in cycles in the directed graph.
     */
    public Set<Node> getNodesInCycles() {
        return nodesInCycles;
    }
}
