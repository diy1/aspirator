package chord.util.graph;

/**
 * Specification of a mutable, labeled, directed graph with useful operations on it.
 * <p>
 * Classes implementing this interface are:
 * <ul>
 * <li>{@link MutableLabeledGraph}, a complete implementation that provides both, the useful operations and a representation of the graph.</li>
 * <li>{@link AbstractGraph}, a partial implementation that provides the useful operations but leaves the representation of the graph unspecified.</li>
 * </ul>
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 *
 * @param <Node> The type of the graph's nodes.
 * @param <Label> The type of the labels on the graph's edges.
 */
public interface IMutableLabeledGraph<Node, Label> extends ILabeledGraph<Node, Label>, IMutableGraph<Node> {
    /**
     * Inserts a given label on a given directed edge in the graph, also inserting the edge and the nodes of the edge if they do not exist in the graph.
     *
     * @param srcNode The source node of the edge.
     * @param dstNode The target node of the edge.
     * @param label The label to be inserted.
     *
     * @return true if the graph is modified, i.e., if an edge from <tt>srcNode</tt> to <tt>dstNode</tt> with label <tt>label</tt> does not exist in the graph.
     */
    public boolean insertLabel(Node srcNode, Node dstNode, Label label);
    /**
     * Removes a given label from a given directed edge in the graph.
     *
     * @param srcNode The source node of the edge.
     * @param dstNode The target node of the edge.
     * @param label The label to be removed.
     *
     * @return true if the graph is modified, i.e., if a edge from <tt>srcNode</tt> to <tt>dstNode</tt> with label <tt>label</tt> exists in the graph.
     */
    public boolean removeLabel(Node srcNode, Node dstNode, Label label);
}
