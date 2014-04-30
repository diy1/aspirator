package chord.util.graph;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Collections;

import chord.util.tuple.object.Pair;
import chord.util.IndexMap;

/**
 * Partial implementation of a directed graph that provides various operations on the graph that are useful for program analysis
 * (e.g., computing SCCs, ordering nodes in RPO, etc.) but leaves the representation of the graph itself unspecified.
 * <p>
 * This class must be extended by clients who wish to use the various operations provided by it but want to provide their own representation of the graph.
 * <p>
 * This class must be used regardless of whether the desired kind of directed graph is mutable or immutable, and labeled or unlabeled.
 * The appropriate interface must be used to convey these characteristics, namely:
 * <ul>
 * <li> {@link IGraph} for the immutable, unlabeled case, </li>
 * <li> {@link ILabeledGraph} for the immutable, labeled case, </li>
 * <li> {@link IMutableGraph} for the mutable, unlabeled case, and </li>
 * <li> {@link IMutableLabeledGraph} for the mutable, labeled case </li>
 * </ul>
 * 
 * @param <Node> The type of the graph's nodes.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public abstract class AbstractGraph<Node> implements IGraph<Node> {
    private List<Node> nodesInRPO = null;
    private IndexMap<Node> nodeMap = null;
    protected boolean cached = false;
    protected void evictCache() {
        nodesInRPO = null;
        nodeMap = null;
        cached = false;
    }
    public boolean isConnected() {
        int numNodes = numNodes();
        Set<Node> reachable = new HashSet<Node>(numNodes);
        for (Node node : getRoots())
            reachable.add(node);
        boolean changed;
        do {
            changed = false;
            for (Node node : getNodes()) {
                if (reachable.contains(node)) {
                    for (Node node2 : getSuccs(node)) {
                        if (reachable.add(node2))
                            changed = true;
                    }
                }
            }
        } while (changed);
        int numReachableNodes = reachable.size();
        assert (numReachableNodes <= numNodes);
        return (numReachableNodes == numNodes);
    }
    public List<Node> getNodesInRPO() {
        if (nodesInRPO == null) {
            nodesInRPO = RPOBuilder.build(this);
            cached = true;
        }
        return nodesInRPO;
    }
    public List<Set<Node>> getTopSortedSCCs() {
        SCCFindingVisitor<Node> visitor = new SCCFindingVisitor<Node>();
        SCCBuilder<Node> builder = new SCCBuilder<Node>(this, visitor);
        builder.build();
        List<Set<Node>> sccList = visitor.getSCCs();
        Collections.reverse(sccList);
        return sccList;
    }
    public Set<Pair<Node, Node>> getBackEdges() {
        return BackEdgesFinder.build(this);
    }
    public boolean hasCycles() {
        // A directed graph has a cycle iff any of the following holds:
        // 1. It has a SCC of size > 1
        // 2. It has a SCC of size = 1 that has a self loop.
        CycleTestingSCCVisitor<Node> visitor =
            new CycleTestingSCCVisitor<Node>(this);
        SCCBuilder<Node> builder = new SCCBuilder<Node>(this, visitor);
        try {
            builder.build();
        } catch (GraphEntityVisitorException ex) {
            return true;
        }
        return false;
    }
    public Set<Node> getNodesInCycles() {
        CycleFindingSCCVisitor<Node> visitor =
            new CycleFindingSCCVisitor<Node>(this);
        SCCBuilder<Node> builder = new SCCBuilder<Node>(this, visitor);
        builder.build();
        return visitor.getNodesInCycles();
    }
    public void getSimpleCycles(IGraphEntityVisitor<Node> visitor) {
        SimpleCyclesFinder.run(this, visitor);
    }
    public ShortestPathBuilder<Node> getShortestPathsBuilder(Node srcNode,
            IPathVisitor<Node> visitor) {
        return new ShortestPathBuilder(this, srcNode, visitor);
    }

    public AllPathsBuilder<Node> getAllPathsBuilder(Node srcNode,
            IPathVisitor<Node> visitor, int maxPathWidth, int maxPathDepth) {
        return new AllPathsBuilder(this, srcNode, visitor,
            maxPathWidth, maxPathDepth);
    }

    public IndexMap<Node> getNodeMap() {
        if (nodeMap == null) {
            nodeMap = new IndexMap<Node>();
            for (Node node : getNodes())
                nodeMap.getOrAdd(node);
        }
        return nodeMap;
    }
    public boolean equals(Object o) {
        if (!(o instanceof IGraph)) {
            return false;
        }
        IGraph<Node> that = (IGraph) o;
        // Note: Order of checks is important for speed.
        // check if they have the same sets of nodes.
        Set<Node> thisNodes = this.getNodes();
        Set<Node> thatNodes = that.getNodes();
        if (!thisNodes.equals(thatNodes)) {
            return false;
        }
        // check if they have the same sets of edges.
        for (Node v : thisNodes) {
            Set<Node> thisPreds = this.getPreds(v);
            Set<Node> thatPreds = that.getPreds(v);
            if (!thisPreds.equals(thatPreds)) {
                return false;
            }
        }
        // check if they have the same sets of roots.
        Set<Node> thisRoots = this.getRoots();
        Set<Node> thatRoots = that.getRoots();
        if (!thisRoots.equals(thatRoots)) {
            return false;
        }
        return true;
    }
    // TODO: This might be a weak hash code since it depends only on the set of nodes in the graph,
    // not on the set of edges or on the set of roots.
    public int hashCode() {
        return getNodes().hashCode();
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
            for (Node node2 : getSuccs(node))
                s += i + " -> " + map.indexOf(node2) + "\n";
        }
        return s;
    }
}
