package chord.util.graph;

/**
 * Specification of a mutable, unlabeled, directed graph with useful operations on it.
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
public interface IMutableGraph<Node> extends IGraph<Node> {
    /**
     * Inserts a given node as a non-root node into the graph.
     *
     * @param node The node to be inserted.
     *
     * @return true if the graph is modified, i.e., if <tt>node</tt> does not exist in the graph.
     */
    public boolean insertNode(Node node);
    /**
     * Inserts a given node as a root node into the graph.
     *
     * @param node The node to be inserted.
     * 
     * @return true if the graph is modified, i.e., if <tt>node</tt> does not exist in the graph or is a non-root node in the graph.
     */
    public boolean insertRoot(Node node);
    /**
     * Designates a given node in the graph as a root node.
     * 
     * @param node A node in the graph.
     * 
     * @return true if the graph is modified, i.e., if <tt>node</tt> is a non-root node in the graph.
     * 
     * @throws RuntimeException if <tt>node</tt> does not exist in the graph.
     */
    public boolean insertRootStrict(Node node);
    /**
     * Removes a given node from the graph while preserving edges incident upon it.
     *
      * This operation removes the following edges from the graph:
     * <pre>
     * 1. for each immediate pred. node1 of node: edge (node1,node)
     * 2. for each immediate succ. node2 of node: edge (node,node2)
     * </pre>
     * This operation inserts the following edges into the graph:
     * <pre>
     * for each immed. pred. node1 of node such that node1 != node:
     *     for each immed. succ. node2 of node such that node2 != node:
     *         edge (node1,node2)
     * </pre>
     * Moreover, if <tt>node</tt> is a root node, then each of its immediate successors is designated a root node.
     *
     * @param node The node to be removed.
     *
     * @return true if the graph is modified, i.e., if <tt>node</tt> exists in the graph.
     *
     * @see #removeNode
     */
    public boolean bypassNode(Node node);
    /**
     * Designates a given node in the graph as a non-root node.
     * 
     * @param node A node in the graph.
     *
     * @return true if the graph is modified, i.e., if <tt>node</tt> is a root node in the graph.
     * 
     * @throws RuntimeException if <tt>node</tt> does not exist in the graph.
     */
    public boolean removeRootStrict(Node node);
    /**
     * Removes a given node from the graph along with edges incident upon it.
     *
     * @param node A node.
     *
     * @return true if the graph is modified, i.e., if <tt>node</tt> exists in the graph.
     *
     * @see #bypassNode
     */
    public boolean removeNode(Node node);
    /**
     * Replaces a given node in the graph by another given node.
     * 
     * @param oldNode The old node.
     * @param newNode The new node.
     * 
     * @return true if the graph is modified, i.e., if <tt>oldNode</tt> exists in the graph and <tt>newNode</tt> is different from it.
     */
    public boolean replaceNode(Node oldNode, Node newNode);
    /**
     * Inserts a given directed edge into the graph, also inserting the nodes of the edge if they do not exist in the graph.
     *
     * @param srcNode The source node of the edge to be inserted.
     * @param dstNode The target node of the edge to be inserted.
     *
     * @return true if the graph is modified, i.e., if an edge from <tt>srcNode</tt> to <tt>dstNode</tt> does not exist in the graph.
     */
    public boolean insertEdge(Node srcNode, Node dstNode);
    /**
     * Inserts a given directed edge into the graph, presuming that the nodes of the edge exist in the graph.
     * 
     * @param srcNode The source node of the edge to be inserted.
     * @param dstNode The target node of the edge to be inserted.
     * 
     * @return true if the graph is modified, i.e., if an edge from <tt>srcNode</tt> to <tt>dstNode</tt> does not exist in the graph.
     * 
     * @throws RuntimeException if <tt>srcNode</tt> or <tt>dstNode</tt> does not exist in the graph.
     */
    public boolean insertEdgeStrict(Node srcNode, Node dstNode);
    /**
     * Removes a given directed edge from the graph.
     *
     * @param srcNode The source node of the edge to be removed.
     * @param dstNode The target node of the edge to be removed.
     * 
     * @return true if the graph is modified, i.e., if an edge from <tt>srcNode</tt> to <tt>dstNode</tt> exists in the graph.
     */
    public boolean removeEdge(Node srcNode, Node dstNode);
    /**
     * Computes the transitive closure of the graph and updates it with the result.
     */
    public void computeTransitiveClosure();
    /**
     * Computes the union of this graph with a given directed graph, and updates this graph with the result.
     * 
     * @param graph A directed graph.  It must be non-null.
     */
    public void union(IGraph<Node> graph);
}
