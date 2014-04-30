package chord.util.graph;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

import chord.util.ArraySet;
import chord.util.IndexMap;

/**
 * Complete implementation of a mutable, unlabeled, directed graph with useful operations on it.
 * <p>
 * This class must be used by clients who wish to use both, the operations inherited by it (e.g., computing SCCs, ordering nodes in RPO, etc.) 
 * and the representation of the graph provided by it.
 * <p>
 * This class must be used regardless of whether the desired kind of unlabeled, directed graph is immutable or mutable.
 * The appropriate interface must be used to convey these characteristics, namely:
 * <ul>
 * <li>{@link IGraph} for the immutable case, and</li>
 * <li>{@link IMutableGraph} for the mutable case.</li>
 * </ul>
 *
 * @param <Node> The type of the graph's nodes.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class MutableGraph<Node> extends AbstractGraph<Node> implements IMutableGraph<Node> {
    private static final long serialVersionUID = -2677984531691568436L;
    private final Set<Node> emptyNodeSet = Collections.emptySet();
    /**
     * Set of root nodes of the graph.
     */
    protected Set<Node> roots;
    /**
      * Map from each node in the graph to the set of all its immediate predecessors.
      */
    protected Map<Node, Set<Node>> nodeToPreds;
     /**
      * Map from each node in the graph to the set of all its immediate successors.
      */
    protected Map<Node, Set<Node>> nodeToSuccs;
    /**
     * Constructs an empty mutable, unlabeled, directed graph.
     */
    public MutableGraph() {
        this(new ArraySet<Node>(), new HashMap<Node, Set<Node>>(), new HashMap<Node, Set<Node>>());
    }
    /**
     * Constructs a mutable, unlabeled directed graph as a copy of the specified graph.
     * Any mutable operations performed henceforth on the specified graph do not affect this graph.
     */
    public MutableGraph(IGraph<Node> graph) {
        this.roots = new ArraySet<Node>(graph.getRoots());
        int numNodes = graph.numNodes();
        this.nodeToPreds = new HashMap<Node, Set<Node>>(numNodes);
        this.nodeToSuccs = new HashMap<Node, Set<Node>>(numNodes);
        for (Node node : graph.getNodes()) {
            Set<Node> preds = new ArraySet<Node>(graph.getPreds(node));
            Set<Node> succs = new ArraySet<Node>(graph.getSuccs(node));
            nodeToPreds.put(node, preds);
            nodeToSuccs.put(node, succs);
        }
    }
    /**
     * Constructs a mutable, unlabeled, directed graph with the provided roots, nodes, and edges.
     * <p>
      * If both maps <tt>nodeToPreds</tt> and <tt>nodeToSuccs</tt> are null, then an empty graph is constructed.
     * <p>
     * If both maps <tt>nodeToPreds</tt> and <tt>nodeToSuccs</tt> are non-null, then they are checked for consistency, namely,
     * <tt>node1</tt> must be specified as an immediate predecessor of <tt>node2</tt> in map <tt>nodeToPreds</tt> iff
     * <tt>node2</tt> is specified as an immediate successor of <tt>node1</tt> in map <tt>nodeToSuccs</tt>.
     * <p>
     * If map <tt>nodeToPreds</tt> is non-null, then the following three conditions must hold:
     * <ul>
     * <li><tt>nodeToPreds.keySet()</tt> must not contain null (that is, the graph cannot have a null node),</li>
     * <li><tt>nodeToPreds.values()</tt> must not contain null (that is, the map must specify the set of immediate
     * predecessor nodes of each node in the graph, even if that set is empty, and</li>
     * <li>for each node <tt>node1</tt> in <tt>nodeToPreds.keySet()</tt>, each node <tt>node2</tt> in
     * <tt>nodeToPreds.get(node1)</tt> must be contained in <tt>nodeToPreds.keySet()</tt>.</li>
     * </ul>
     * <p>
     * If map <tt>nodeToSuccs</tt> is non-null, then the following three conditions must hold:
     * <ul>
     * <li><tt>nodeToSuccs.keySet()</tt> must not contain null (that is, the graph cannot have a null node),</li>
     * <li><tt>nodeToPreds.values()</tt> must not contain null (that is, the map must specify the set of immediate
     * successor nodes of each node in the graph, even if that set is empty, and</li>
     * <li>for each node <tt>node1</tt> in <tt>nodeToSuccs.keySet()</tt>, each node <tt>node2</tt> in
     * <tt>nodeToSuccs.get(node1)</tt> must be contained in <tt>nodeToSuccs.keySet()</tt>.</li>
     * </ul>
     *
     * @param roots A set of nodes designated as roots of the graph.
     *        It may be null, in which case every node in the graph is treated as a root node.
     * @param nodeToPreds A map from each node in the graph to the set of all its immediate predecessor nodes.
     *        It may be null.
     * @param nodeToSuccs A map from each node in the graph to the set of all its immediate successor nodes.
     *        It may be null.
     */
    public MutableGraph(Set<Node> roots, Map<Node, Set<Node>> nodeToPreds, Map<Node, Set<Node>> nodeToSuccs) {
        if (nodeToPreds == null) {
            if (nodeToSuccs == null) {
                this.nodeToPreds = new HashMap<Node, Set<Node>>();
                this.nodeToSuccs = new HashMap<Node, Set<Node>>();
            } else {
                validate(nodeToSuccs, "nodeToSuccs");
                this.nodeToSuccs = nodeToSuccs;
                computeNodeToPredsMap();
            }
        } else {
            if (nodeToSuccs == null) {
                validate(nodeToPreds, "nodeToPreds");
                this.nodeToPreds = nodeToPreds;
                computeNodeToSuccsMap();
            } else {
                this.nodeToPreds = nodeToPreds;
                this.nodeToSuccs = nodeToSuccs;
                validateNonRoots();
            }
        }
        this.roots = roots;
        validateRoots();
    }
    public int numRoots() {
        return roots.size();
    }
    public int numNodes() {
        return nodeToPreds.size();
    }
    public int numPreds(Node node) {
        Set<Node> preds = nodeToPreds.get(node);
        if (preds == null)
            return 0;
        return preds.size();
    }
    public int numSuccs(Node node) {
        Set<Node> succs = nodeToSuccs.get(node);
        if (succs == null)
            return 0;
        return succs.size();
    }
    public boolean hasRoot(Node node) {
        return roots.contains(node);
    }
    public boolean hasNode(Node node) {
        return nodeToPreds.containsKey(node);
    }
    public boolean hasEdge(Node node1, Node node2) {
        Set<Node> preds = nodeToPreds.get(node2);
        if (preds == null)
            return false;
        return preds.contains(node1);
    }
    public Set<Node> getRoots() {
        return roots;
    }
    public Set<Node> getNodes() {
        return nodeToPreds.keySet();
    }
    public Set<Node> getPreds(Node node) {
        Set<Node> preds = nodeToPreds.get(node);
        if (preds == null)
            return emptyNodeSet;
        return preds;
    }
    public Set<Node> getSuccs(Node node) {
        Set<Node> succs = nodeToSuccs.get(node);
        if (succs == null)
            return emptyNodeSet;
        return succs;
    }
    public boolean insertNode(Node v) {
        if (!nodeToPreds.containsKey(v)) {
            nodeToPreds.put(v, new ArraySet<Node>());
            nodeToSuccs.put(v, new ArraySet<Node>());
            if (cached)
                evictCache();
            return true;
        } 
        return false;
    }
    public boolean insertRoot(Node v) {
        if (!nodeToPreds.containsKey(v)) {
            nodeToPreds.put(v, new ArraySet<Node>());
            nodeToSuccs.put(v, new ArraySet<Node>());
        }
        boolean ret = roots.add(v);
        if (ret) {
            if (cached)
                evictCache();
            return true;
        }
        return false;
    }
    public boolean insertRootStrict(Node v) {
        if (!nodeToPreds.containsKey(v))
            throw new RuntimeException();
        boolean ret = roots.add(v);
        return ret;
    }
    public boolean bypassNode(Node v) {
        assert (v != null);
        Set<Node> Pv = nodeToPreds.get(v);
        if (Pv == null) {
            // node v does not exist in the graph
            return false;
        }
        Set<Node> Sv = nodeToSuccs.get(v);
        nodeToPreds.remove(v);
        nodeToSuccs.remove(v);
        // Remove self loops involving v, if any.
        if (Pv.remove(v)) {
            Sv.remove(v);
        }
        if (roots.remove(v)) {
            for (Node w : Sv)
                roots.add(w);
        }
        for (Node u : Pv) {
            Set<Node> Su = nodeToSuccs.get(u);
            Su.remove(v);
            Su.addAll(Sv);
        }
        for (Node w : Sv) {
            Set<Node> Pw = nodeToPreds.get(w);
            Pw.remove(v);
            Pw.addAll(Pv);
        }
        if (cached)
            evictCache();
        return true;
    }
    public boolean removeRootStrict(Node v) {
        if (!nodeToPreds.containsKey(v))
            throw new RuntimeException();
        boolean ret = roots.remove(v);
        if (ret) {
            if (cached)
                evictCache();
            return true;
        }
        return false;
    }
    public boolean removeNode(Node v) {
        Set<Node> Pv = nodeToPreds.get(v);
        if (Pv == null) {
            // node v does not exist in the graph
            return false;
        }
        Set<Node> Sv = nodeToSuccs.get(v);
        roots.remove(v);
        nodeToPreds.remove(v);
        nodeToSuccs.remove(v);
        // Remove self loops involving v, if any
        if (Pv.remove(v)) {
            Sv.remove(v);
        }
        for (Node u : Pv) {
            Set<Node> Su = nodeToSuccs.get(u);
            Su.remove(v);
        }
        for (Node w : Sv) {
            Set<Node> Pw = nodeToPreds.get(w);
            Pw.remove(v);
        }
        if (cached)
            evictCache();
        return true;
    }
    public boolean replaceNode(Node oldNode, Node newNode) {
        // validate();
        // check if oldNode is the same as newNode
        if (oldNode == null) {
            if (newNode == null)
                return false;
        } else {
            if (newNode != null && oldNode.equals(newNode))
                return false;
        }
        // at this point we have oldNode != newNode
        Set<Node> Pv = nodeToPreds.get(oldNode);
        if (Pv == null) {
            // oldNode does not exist in the graph
            return false;
        }
        Set<Node> Sv = nodeToSuccs.get(oldNode);
        nodeToPreds.remove(oldNode);
        nodeToSuccs.remove(oldNode);
        boolean hasSelfLoop;
        if (Pv.remove(oldNode)) {
            hasSelfLoop = true;
            Sv.remove(oldNode);
        } else
            hasSelfLoop = false;
        for (Node u : Pv) {
            Set<Node> Su = nodeToSuccs.get(u);
            Su.remove(oldNode);
            Su.add(newNode);
        }
        for (Node w : Sv) {
            Set<Node> Pw = nodeToPreds.get(w);
            Pw.remove(oldNode);
            Pw.add(newNode);
        }
        if (hasSelfLoop) {
            Pv.add(newNode);
            Sv.add(newNode);
        }
        if (roots.remove(oldNode)) {
            roots.add(newNode);
        }
        Set<Node> Px = nodeToPreds.get(newNode);
        if (Px == null) {
            nodeToPreds.put(newNode, Pv);
            nodeToSuccs.put(newNode, Sv);
            if (cached)
                evictCache();
            return true;
        }
        Set<Node> Sx = nodeToSuccs.get(newNode);
        Px.addAll(Pv);
        Sx.addAll(Sv);
        if (cached)
            evictCache();
        return true;
    }
    public boolean insertEdge(Node u, Node v) {
        Set<Node> Su = nodeToSuccs.get(u);
        if (Su == null) {
            Su = new ArraySet<Node>();
            nodeToSuccs.put(u, Su);
            nodeToPreds.put(u, new ArraySet<Node>());
        }
        Set<Node> Pv = nodeToPreds.get(v);
        // if u is the same as v then Pv != null
        if (Pv == null) {
            Pv = new ArraySet<Node>();
            nodeToPreds.put(v, Pv);
            nodeToSuccs.put(v, new ArraySet<Node>());
        }
        if (Su.contains(v)) {
            // edge (u,v) already exists in the graph
            return false;
        }
        Su.add(v);
        Pv.add(u);
        if (cached)
            evictCache();
        return true;
    }
    public boolean insertEdgeStrict(Node u, Node v) {
        Set<Node> Su = nodeToSuccs.get(u);
        if (Su == null) {
            throw new RuntimeException();
        }
        Set<Node> Pv = nodeToPreds.get(v);
        if (Pv == null) {
            throw new RuntimeException();
        }
        if (Su.contains(v)) {
            // edge (u,v) already exists in the graph.
            return false;
        }
        Su.add(v);
        Pv.add(u);
        if (cached)
            evictCache();
        return true;
    }
    public boolean removeEdge(Node u, Node v) {
        Set<Node> Su = nodeToSuccs.get(u);
        Set<Node> Pv = nodeToPreds.get(v);
        if (Su == null || Pv == null) {
            // u and/or v does not exist in the graph.
            return false;
        }
        if (!Su.contains(v)) {
            // edge (u,v) does not exist in the graph.
            return false;
        }
        Su.remove(v);
        Pv.remove(u);
        if (cached)
            evictCache();
        return true;
    }
    /** 
     * Computes the transitive closure of the graph.
     * Considers each pair of edges (u,v) and (v,w) in the graph such that u != v != w, and adds edge (u,w) if it is not in the graph.
     * The process terminates after no more edges can be added.
     */
    public void computeTransitiveClosure() {
        Set<Node> nodes = nodeToPreds.keySet();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Node v : nodes) {
                Set<Node> Pv = nodeToPreds.get(v);
                Set<Node> Sv = nodeToSuccs.get(v);
                for (Node u : Pv) {
                    if (u != v) { 
                        Set<Node> Su = nodeToSuccs.get(u);
                        for (Node w : Sv) {
                            if (v != w && u != w && Su.add(w)) {
                                Set<Node> Pw = nodeToPreds.get(w);
                                Pw.add(u);
                                changed = true;
                            }
                        }
                    }
                }
            }
        }
        // validate();
        if (cached)
            evictCache();
    }
    public void union(IGraph<Node> that) {
        for (Node v : that.getNodes()) {
            Set<Node> thatPreds = that.getPreds(v);
            Set<Node> thatSuccs = that.getSuccs(v);
            Set<Node> thisPreds;
            Set<Node> thisSuccs;
            if (nodeToPreds.containsKey(v)) {
                thisPreds = nodeToPreds.get(v);
                thisSuccs = nodeToSuccs.get(v);
                thisPreds.addAll(thatPreds);
                thisSuccs.addAll(thatSuccs);
            } else {
                thisPreds = new ArraySet<Node>(thatPreds);
                thisSuccs = new ArraySet<Node>(thatSuccs);
                nodeToPreds.put(v, thisPreds);
                nodeToSuccs.put(v, thisSuccs);
            }
        }   
        roots.addAll(that.getRoots());
        // validate();
        if (cached)
            evictCache();
    }
    public void validate() {
        validateNonRoots();
        validateRoots();
    }
    private void validateNonRoots() {
        validate(nodeToPreds, "nodeToPreds");
        validate(nodeToSuccs, "nodeToSuccs");
        for (Map.Entry<Node, Set<Node>> e : nodeToPreds.entrySet()) {
            Node v = e.getKey();
            Set<Node> preds = e.getValue();
            for (Node u : preds) {
                Set<Node> succs = nodeToSuccs.get(u);
                assert (succs != null);
                assert (succs.contains(v));
            }
        }
        for (Map.Entry<Node, Set<Node>> e : nodeToSuccs.entrySet()) {
            Node v = e.getKey();
            Set<Node> succs = e.getValue();
            for (Node w : succs) {
                Set<Node> preds = nodeToPreds.get(w);
                assert (preds != null);
                assert (preds.contains(v));
            }
        }
    }
    private void validateRoots() {
        assert (roots != null);
        for (Node v : roots) {
            assert (nodeToPreds.containsKey(v));
        }
    }
    private void validate(Map<Node, Set<Node>> map, String name) {
        assert (map != null);
        for (Map.Entry<Node, Set<Node>> e : map.entrySet()) {
            Node v = e.getKey();
            Set<Node> s = e.getValue();
            if (s == null) {
                assert false : "Map " + name + " must map node '" + v +
                    "' to a non-null set (even if the set is empty).";
            }
            for (Node u : s) {
                if (!map.containsKey(u)) {
                    assert false : "Map " + name + " must contain node '" + u +
                        "' in its domain since it is contained in the set " +
                        "of nodes to which node '" + v + "' is mapped.";
                }
            }
        }
    }
    private void computeNodeToSuccsMap() {
        Set<Node> nodes = nodeToPreds.keySet();
        int numNodes = nodes.size();
        IndexMap<Node> nodeToId = new IndexMap<Node>(numNodes);
        int[] nodeToNumSuccs = new int[numNodes];
        for (Node u : nodes)
            nodeToId.getOrAdd(u);
        for (Node u : nodes) {
            for (Node v : nodeToPreds.get(u))
                nodeToNumSuccs[nodeToId.indexOf(v)]++; 
        }
        nodeToSuccs = new HashMap<Node, Set<Node>>(numNodes);
        for (Node v : nodes) {
            int numSuccs = nodeToNumSuccs[nodeToId.indexOf(v)];
            nodeToSuccs.put(v, new ArraySet<Node>(numSuccs));
        }
        for (Node u : nodes) {
            for (Node v : nodeToPreds.get(u)) {
                Set<Node> succs = nodeToSuccs.get(v);
                succs.add(u);
            }
        }
    }
    private void computeNodeToPredsMap() {
        Set<Node> nodes = nodeToSuccs.keySet();
        int numNodes = nodes.size();
        IndexMap<Node> nodeToId = new IndexMap<Node>(numNodes);
        int[] nodeToNumPreds = new int[numNodes];
        for (Node u : nodes)
            nodeToId.getOrAdd(u);
        for (Node u : nodes) {
            for (Node v : nodeToSuccs.get(u))
                nodeToNumPreds[nodeToId.indexOf(v)]++; 
        }
        nodeToPreds = new HashMap<Node, Set<Node>>(numNodes);
        for (Node v : nodes) {
            int numPreds = nodeToNumPreds[nodeToId.indexOf(v)];
            nodeToPreds.put(v, new ArraySet<Node>(numPreds));
        }
        for (Node u : nodes) {
            for (Node v : nodeToSuccs.get(u)) {
                Set<Node> preds = nodeToPreds.get(v);
                preds.add(u);
            }
        }
    }
}
