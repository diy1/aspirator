package chord.util.graph;

import java.util.HashSet;
import java.util.Set;

import chord.util.tuple.object.Pair;

/**
 * Algorithm for finding all back edges in a directed graph.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class BackEdgesFinder<Node> {
    /**
     * Provides all back edges in a depth-first traversal of a given directed graph reachable from its root nodes.
     * 
     * @param graph A directed graph.
     * 
     * @return All back edges in a depth-first traversal of the given directed graph reachable from its root nodes.
     */
    public static <Node> Set<Pair<Node, Node>> build(IGraph<Node> graph) {
        return (new BackEdgesFinder<Node>(graph)).backEdges;
    }
    private final IGraph<Node> graph;
    private final Set<Pair<Node, Node>> backEdges;
    private final Set<Node> visitedBef;
    private final Set<Node> visitedAft;
    private BackEdgesFinder(IGraph<Node> graph) {
        this.graph = graph;
        int numNodes = graph.numNodes();
        visitedBef = new HashSet<Node>(numNodes);
        visitedAft = new HashSet<Node>(numNodes);
        backEdges = new HashSet<Pair<Node, Node>>();
        for (Node root : graph.getRoots()) {
            if (!visitedBef.contains(root))
                visit(root);
        }
    }
    private void visit(Node v) {
        visitedBef.add(v);
        for (Node w : graph.getSuccs(v)) {
            if (visitedBef.contains(w)) {
                if (!visitedAft.contains(w))
                    backEdges.add(new Pair<Node, Node>(v, w));
            } else
                visit(w);
        }
        visitedAft.add(v);
    }
}
