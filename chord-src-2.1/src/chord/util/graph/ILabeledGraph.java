package chord.util.graph;

import java.util.Set;

/**
 * Specification of an immutable, labeled, directed graph with useful operations on it.
 * <p>
 * This interface must be used when it is desirable to provide an immutable view of a labeled, directed graph to clients even though the underlying
 * implementation may be that of a mutable graph.
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
public interface ILabeledGraph<Node, Label> extends IGraph<Node> {
    /**
     * Provides the set of all labels on a given directed edge in the graph.
     * 
     * @param srcNode The source node of the edge.
     * @param dstNode The target node of the edge.
     * 
     * @return The set of all labels on the edge from <tt>srcNode</tt> to <tt>dstNode</tt> in the graph.
     *         It is the empty set if either node does not exist, the edge does not exist, or no labels exist on the edge in the graph.
     */
    public Set<Label> getLabels(Node srcNode, Node dstNode);
}
