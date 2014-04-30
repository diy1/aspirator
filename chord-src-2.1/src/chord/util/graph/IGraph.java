package chord.util.graph;

import java.util.List;
import java.util.Set;

import chord.util.IndexMap;
import chord.util.tuple.object.Pair;

/**
 * Specification of an immutable, unlabeled, directed graph with useful operations on it.
 * <p>
 * This interface must be used when it is desirable to provide an immutable view of an unlabeled, directed graph to clients even though the underlying
 * implementation may be that of a mutable graph.
 * <p>
 * Classes implementing this interface are:
 * <ul>
 * <li>{@link MutableGraph}, a complete implementation that provides both, the useful operations and a representation of the graph.</li>
 * <li>{@link AbstractGraph}, a partial implementation that provides the useful operations but leaves the representation of the graph unspecified.</li>
 * </ul>
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 *
 * @param <Node> The type of the graph's nodes.
 */
public interface IGraph<Node> extends java.io.Serializable {
    /**
     * Determines whether this graph contains a given node as a root node.
     * 
     * @param node A node.
     * 
     * @return true if this graph contains the given node as a root node.
     */
    public boolean hasRoot(Node node);
    /**
     * Determines whether this graph contains a given node.
     *
     * @param node A node.
     *
     * @return true if this graph contains the given node.
     */
    public boolean hasNode(Node node);
    /**
     * Determines whether this graph contains a given directed edge.
     * 
     * @param node1 A node.
     * @param node2 A node.
     * 
     * @return true if this graph contains a directed edge from <tt>node1</tt> to <tt>node2</tt>.
     */
    public boolean hasEdge(Node node1, Node node2);
    /**
     * Provides the total number of roots of this graph.
     *
     * @return The total number of roots of this graph.
     */
    public int numRoots();
    /**
     * Provides the total number of nodes in this graph.
     *
     * @return The total number of nodes in this graph.
     */
    public int numNodes();
    /**
     * Provides the number of immediate predecessors of a given node in this graph.
     * 
     * @param node A node.
     * 
     * @return The number of immediate predecessors of the given node, if it exists in this graph, and 0 otherwise.
     */
    public int numPreds(Node node);
    /**
     * Provides the number of immediate successors of a given node in this graph.
     * 
     * @param node A node.
     * 
     * @return The number of immediate successors of the given node, if it exists in this graph, and 0 otherwise.
     */
    public int numSuccs(Node node);
    /**
     * Provides all root nodes of this graph.
     *
     * @return All root nodes of this graph.
     */
    public Set<Node> getRoots();
    /**
     * Provides all nodes in this graph.
     *
     * @return All nodes in this graph.
     */
    public Set<Node> getNodes();
    /**
     * Provides all immediate predecessors of a given node in this graph.
     *
     * @param node A node.
     *
     * @return All immediate predecessors of the given node.
     */
    public Set<Node> getPreds(Node node);
    /**
     * Provides all immediate successors of a given node in this graph.
     *
     * @param node A node.
     *
     * @return All immediate successors of the given node.
     */
    public Set<Node> getSuccs(Node node);
    /**
     * Determines whether each node in the graph is reachable from some root node.
     *
     * @return true if each node in the graph is reachable from some root node.
     */
    public boolean isConnected();
    /**
     * Provides all nodes in the graph reachable from the root nodes, ordered in Reverse Post Order (RPO).
     * 
     * @return All nodes in the graph reachable from the root nodes, ordered in RPO.
     */
    public List<Node> getNodesInRPO();
    /**
     * Provides the list of Strongly Connected Components (SCCs) of the graph reachable from its root nodes, sorted in topological order.
     *
     * @return The list of SCCs of the graph reachable from its root nodes, sorted in topological order.
     */
    public List<Set<Node>> getTopSortedSCCs();
    /**
     * Provides all back edges in a depth-first traversal of the graph reachable from the root nodes.
     * 
     * @return All back edges in a depth-first traversal of the graph reachable from its root nodes.
     */
    public Set<Pair<Node, Node>> getBackEdges();
    /**
     * Determines whether the graph reachable from the root nodes contains any cycles.
     *
     * @return true if the graph reachable from the root nodes contains any cycles.
     */
    public boolean hasCycles();
    /**
     * Provides all nodes of the graph reachable from the root nodes that are contained in cycles.
     * 
     * @return All nodes of the graph reachable from the root nodes that are contained in cycles.
     */
    public Set<Node> getNodesInCycles();
    /**
     * Computes all simple cycles of this graph.
     * 
     * @param visitor A visitor over the graph's simple cycles.
     */
    public void getSimpleCycles(IGraphEntityVisitor<Node> visitor);
    /**
     * Provides a map from each node in this graph to a unique arbitrary integer in the range [0..N] where N is the total number of nodes in the graph.
     * 
     * @return A map from each node in the graph to a unique arbitrary integer.
     */
    public IndexMap<Node> getNodeMap();

    public ShortestPathBuilder<Node> getShortestPathsBuilder(Node srcNode, IPathVisitor<Node> visitor);

    public AllPathsBuilder<Node> getAllPathsBuilder(Node srcNode, IPathVisitor<Node> visitor, int maxPathWidth, int maxPathDepth);
}
