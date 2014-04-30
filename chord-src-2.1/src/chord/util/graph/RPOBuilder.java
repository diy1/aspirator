package chord.util.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Algorithm for computing the Reverse Post Order (RPO) of the nodes in a directed graph.
 * <p>
 * Notes:
 * <ul>
 * <li> Any parts of the directed graph unreachable from its root nodes are silently ignored. </li>
 * <li> If the graph is acyclic (i.e. it is a DAG) then the RPO is a Topological Order. </li>
 * </ul>
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public final class RPOBuilder<Node> {
    /**
     * Provides all nodes of a given directed graph reachable from its root nodes and ordered in Reverse Post Order (RPO).
     * 
     * @param graph A directed graph.
     * 
     * @return All nodes of the given directed graph reachable from its root nodes and ordered in RPO.
     */
    public static <Node> List<Node> build(IGraph<Node> graph) {
        return (new RPOBuilder<Node>(graph)).postOrder;
    }
    private final IGraph<Node> graph;
    private final Set <Node> visited;
    private final List<Node> postOrder;
    private RPOBuilder(IGraph<Node> graph) {
        this.graph = graph;
        int numNodes = graph.numNodes();
        visited = new HashSet<Node>(numNodes);
        postOrder = new ArrayList<Node>(numNodes);
        for (Node v : graph.getRoots()) {
            if (visited.add(v)) {
                visit(v);
            }
        }
        Collections.reverse(postOrder);
    }
    private void visit(Node v) {
        for (Node w : graph.getSuccs(v)) {
            if (visited.add(w))
                visit(w);
        }
        postOrder.add(v);
    }
}

