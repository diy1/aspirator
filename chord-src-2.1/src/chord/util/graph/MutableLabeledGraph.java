package chord.util.graph;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Collections;

import chord.util.ArraySet;
import chord.util.IndexMap;
import chord.util.tuple.object.Pair;

/**
 * Complete implementation of a mutable, labeled, directed graph with useful operations on it.
 * <p>
 * This class must be used by clients who wish to use both, the operations inherited by it (e.g., computing SCCs, ordering nodes in RPO, etc.)
 * and the representation of the graph provided by it.
 * <p>
 * This class must be used regardless of whether the desired kind of labeled, directed graph is immutable or mutable.
 * The appropriate interface must be used to convey these characteristics, namely:
 * <ul>
 * <li> {@link ILabeledGraph} for the immutable case, and </li>
 * <li> {@link IMutableLabeledGraph} for the mutable case. </li>
 * </ul>

 * @param    <Node>    The type of the graph's nodes.
 * @param    <Label>    The type of the labels on the graph's edges.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class MutableLabeledGraph<Node, Label> extends MutableGraph<Node> implements IMutableLabeledGraph<Node, Label> {
    private static final long serialVersionUID = -8079798444618962101L;
    private final Set<Label> emptyLabelSet = Collections.emptySet();
    /**
     * Map from each pair of nodes (u,v) to the set containing each label l such that u->v is an edge labeled l in the graph.
     */
    protected Map<Pair<Node, Node>, Set<Label>> nodesToLabels;
    /**
     * Constructs an empty mutable, labeled, directed graph.
     */
    public MutableLabeledGraph() {
        nodesToLabels = new HashMap<Pair<Node, Node>, Set<Label>>();
    }
    /**
     * Constructs a mutable, labeled, directed graph with the specified roots, nodes, edges, and labels on edges.
     *
     * @param roots
     * @param nodeToPreds
     * @param nodeToSuccs
     * @param nodesToLabels
     */
    public MutableLabeledGraph(Set<Node> roots,
            Map<Node, Set<Node>> nodeToPreds,
            Map<Node, Set<Node>> nodeToSuccs,
            Map<Pair<Node, Node>, Set<Label>> nodesToLabels) {
        super(roots, nodeToPreds, nodeToSuccs);
        this.nodesToLabels = nodesToLabels;
    }
    public void validate() {
        throw new RuntimeException("not impl");
    }
    public String toString() {
        IndexMap<Node> map = new IndexMap<Node>(numNodes());
        String s = "";
        for (Node node : getNodes())
            s += "Node " + map.getOrAdd(node) + ": " + node + "\n";
        s += "Roots: ";
        for (Node node : getRoots())
            s += map.indexOf(node) + " ";
        s += "\nEdges:\n";
        for (Node node : getNodes()) {
            int i = map.indexOf(node);
             for (Node node2 : getSuccs(node)) {
                s += i + " -> " + map.indexOf(node2) + " ";
                Pair<Node, Node> edge = new Pair<Node, Node>(node, node2);
                Set<Label> labels = nodesToLabels.get(edge);
                if (labels == null)
                    s += "[ ]";
                else {
                    for (Label label : labels)
                        s += "[" + label + "] ";
                }
                s += "\n";
             }
        }
        return s;
    }
    public boolean insertLabel(Node srcNode, Node dstNode, Label label) {
        insertEdge(srcNode, dstNode);
        Pair<Node, Node> edge = new Pair<Node, Node>(srcNode, dstNode);
        Set<Label> labels = nodesToLabels.get(edge);
        if (labels == null) {
            labels = new ArraySet<Label>();
            nodesToLabels.put(edge, labels);
        }
        return labels.add(label);
    }
    public boolean removeLabel(Node srcNode, Node dstNode, Label label) {
        Pair<Node, Node> edge = new Pair<Node, Node>(srcNode, dstNode);
        Set<Label> labels = nodesToLabels.get(edge);
        if (labels == null)
            return false;
        return labels.remove(label);
    }
    public Set<Label> getLabels(Node srcNode, Node dstNode) {
        Pair<Node, Node> edge = new Pair<Node, Node>(srcNode, dstNode);
        Set<Label> labels = nodesToLabels.get(edge);
        if (labels == null)
            return emptyLabelSet;
        return labels;
    }
    public boolean removeNode(Node node) {
        throw new UnsupportedOperationException("not impl");
    }
    public boolean bypassNode(Node node) {
        throw new UnsupportedOperationException("not impl");
    }
    public boolean replaceNode(Node oldNode, Node newNode) {
        throw new UnsupportedOperationException("not impl");
    }
    public boolean removeEdge(Node srcNode, Node dstNode) {
        throw new UnsupportedOperationException("not impl");
    }
    public void union(IGraph<Node> graph) {
        throw new UnsupportedOperationException("not impl");
    }
}
