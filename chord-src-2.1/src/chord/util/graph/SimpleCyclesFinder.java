package chord.util.graph;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import gnu.trove.stack.array.TIntArrayStack;
import chord.util.ArraySet;
import chord.util.IndexMap;

/**
 * Algorithm for finding all simple cycles in a directed graph.
 *  
 * @author Mayur Naik (mhn@cs.stanford.edu)
 *
 * @param <Node> The type of the graph's nodes.
 */
public class SimpleCyclesFinder<Node> {
    private final IGraph<Node> graph;
    private final IGraphEntityVisitor<Node> visitor;
    private Map<Node, Set<Node>> nodeToSuccsMap; 
    private IndexMap<Node> nodeIdxMap;
    private int currNodeIdx;
    private TIntArrayStack markedStack;
    private TIntArrayStack pointStack;
    private boolean[] mark;
    /**
     * Finds all simple cycles in a given directed graph by invoking a given visitor on each simple cycle.
     * 
     * @param <Node> The type of the graph's nodes.
     * 
     * @param graph A directed graph.
     * @param visitor A visitor over the graph's simple cycles.
     */
    public static <Node> void run(IGraph<Node> graph, IGraphEntityVisitor<Node> visitor) {
        (new SimpleCyclesFinder<Node>(graph, visitor)).run();
    }
    private SimpleCyclesFinder(IGraph<Node> graph, IGraphEntityVisitor<Node> visitor) {
        this.graph = graph;
        this.visitor = visitor;
    }
    private void run() {
        markedStack = new TIntArrayStack();
        pointStack = new TIntArrayStack();
        nodeToSuccsMap = new HashMap<Node, Set<Node>>();
        nodeIdxMap = new IndexMap<Node>();
        int numNodes = 0;
        for (Node node : graph.getNodes()) {
            numNodes++;
            Set<Node> succs = new ArraySet<Node>();
            nodeToSuccsMap.put(node, succs);
            for (Node node2 : graph.getSuccs(node)) {
                succs.add(node2);
            }
            nodeIdxMap.getOrAdd(node);
        }
        mark = new boolean[numNodes];
        for (currNodeIdx = 0; currNodeIdx < numNodes; currNodeIdx++) {
            backtrack(currNodeIdx);
            while (markedStack.size() != 0) {
                int nodeIdx = markedStack.pop();
                mark[nodeIdx] = false;
            }
        }
    }
    private boolean backtrack(int nodeIdx) {
        boolean f = false;
        pointStack.push(nodeIdx);
        mark[nodeIdx] = true;
        markedStack.push(nodeIdx);
        Node node = nodeIdxMap.get(nodeIdx);
        Iterator<Node> it = nodeToSuccsMap.get(node).iterator();
        while (it.hasNext()) {
            Node succNode = it.next();
            int succNodeIdx = nodeIdxMap.indexOf(succNode); 
            if (succNodeIdx < currNodeIdx)
                it.remove();
            else if (succNodeIdx == currNodeIdx) {
                int[] path = pointStack.toArray();
                visitor.prologue();
                for (int nodeIdx2 : path) {
                    Node node2 = nodeIdxMap.get(nodeIdx2);
                    visitor.visit(node2);
                }
                visitor.epilogue();
                f = true;
            } else if (mark[succNodeIdx] == false) {
                boolean g = backtrack(succNodeIdx);
                f = f | g;
            }
        }
        // f == true if an elem circuit continuing partial path
        // on the stack has been found
        if (f == true) {
            int nodeIdx2;
            do {
                nodeIdx2 = markedStack.pop();
                mark[nodeIdx2] = false;
            } while (nodeIdx2 != nodeIdx);
        }
        pointStack.pop();
        return f;
    }
}
