package chord.util.graph;

import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;

import chord.util.IndexMap;

/**
 * Algorithm for computing the shortest path between a pair of nodes in a directed graph.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class ShortestPathBuilder<Node> {
    private final IPathVisitor<Node> visitor;
    private final IGraph<Node> graph;
    private final int origNodeIdx;
    private final IndexMap<Node> map;
    private final int numNodes;
    private final int[] dist;
    private final int[] prev;
    private final Set<Node> workSet;
    public ShortestPathBuilder(IGraph<Node> graph, Node origNode, IPathVisitor<Node> visitor) {
        this.graph = graph;
        this.visitor = visitor;
        this.map = graph.getNodeMap();
        numNodes = map.size();
        this.dist = new int[numNodes];
        this.prev = new int[numNodes];
        for (int i = 0; i < numNodes; i++) {
            dist[i] = numNodes;
            prev[i] = -1;
        }
        origNodeIdx = map.indexOf(origNode);
        dist[origNodeIdx] = 0;
        prev[origNodeIdx] = origNodeIdx;
         workSet = new HashSet<Node>(numNodes);
        for (Node node : map) {
            workSet.add(node);
        }
    }
    private void findShortestPathTo(Node destNode) {
        if (!workSet.contains(destNode)) {
            // shortest path to destNode has already been found
            // previously
            return;
        }
        int destNodeIdx = map.indexOf(destNode);
        while (!workSet.isEmpty()) {
            Iterator<Node> it = workSet.iterator();
            Node minNode = it.next();
            int minNodeIdx = map.indexOf(minNode);
            int minDist = dist[minNodeIdx];
            while (it.hasNext()) {
                Node currNode = it.next();
                int currNodeIdx = map.indexOf(currNode);
                int currDist = dist[currNodeIdx];
                if (currDist < minDist) {
                    minNode = currNode;
                    minNodeIdx = currNodeIdx;
                    minDist = currDist;
                }
            }
            workSet.remove(minNode);
            for (Node succ : graph.getSuccs(minNode)) {
                if (workSet.contains(succ)) {
                    int v = map.indexOf(succ);
                    int alt = minDist + 1; 
                    if (alt < dist[v]) {
                        dist[v] = alt;
                        prev[v] = minNodeIdx;
                    }
                }
            }
            if (minNodeIdx == destNodeIdx)
                return;
        }
        
    }
    public String getShortestPathTo(Node destNode) {
        findShortestPathTo(destNode);
        int destNodeIdx = map.indexOf(destNode);
        if (prev[destNodeIdx] == -1)
            return null;
        String path = "";
        while (destNodeIdx != origNodeIdx) {
            int prevNodeIdx = prev[destNodeIdx];
            Node prevNode = map.get(prevNodeIdx);
            String t = visitor.visit(prevNode, destNode);
            path = t + path;
            destNodeIdx = prevNodeIdx;
            destNode = prevNode;
        }
        return path;
    }
}
