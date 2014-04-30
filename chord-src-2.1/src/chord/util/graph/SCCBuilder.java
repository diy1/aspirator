package chord.util.graph;

import gnu.trove.list.array.TIntArrayList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Algorithm for computing the Strongly Connected Components (SCCs) of a directed graph.
 * 
 * The algorithm implemented here is
 * <a href="http://en.wikipedia.org/wiki/Tarjan's_strongly_connected_components_algorithm">Tarjan's algorithm.</a>
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class SCCBuilder<Node> {
    private final IGraph<Node> graph;
    private final IGraphEntityVisitor<Node> visitor;
    private Set<Node> visited;
    private ArrayList<Node> stk;
    private TIntArrayList idxStk;
    private int currIdx;
    public SCCBuilder(IGraph<Node> graph, IGraphEntityVisitor<Node> visitor) {
        this.graph = graph;
        this.visitor = visitor;
    }
    public void build() {
        int numNodes = graph.numNodes();
        visited = new HashSet<Node>(numNodes);
        stk = new ArrayList<Node>();
        idxStk = new TIntArrayList();
        for (Node v : graph.getRoots()) {
            if (visited.add(v))
                visit(v);
        }
    }
    private int visit(Node v) {
        int vIdx = currIdx;
        int vLow = currIdx;
        currIdx++;
        stk.add(v);
        idxStk.add(vIdx);
        for (Node w : graph.getSuccs(v)) {
            if (visited.add(w)) {
                int wLow = visit(w);
                vLow = vLow < wLow ? vLow : wLow;
                continue;
            }    
            int i = stk.indexOf(w);
            if (i != -1) {
                int wIdx = idxStk.get(i);
                vLow = vLow < wIdx ? vLow : wIdx;
            }
        }
        if (vLow == vIdx) {
            visitor.prologue();
            int n = stk.size() - 1;
            Node w;
            do {
                w = stk.remove(n);
                idxStk.remove(n);
                visitor.visit(w);
                n--;
            } while (w != v);
            visitor.epilogue();
        }
        return vLow;
    }
}
