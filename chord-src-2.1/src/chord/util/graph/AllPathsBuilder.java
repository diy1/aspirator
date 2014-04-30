package chord.util.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

/**
 * Algorithm for computing all paths between a pair of nodes in a directed graph.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class AllPathsBuilder<Node> {
    private final IGraph<Node> graph;
    private final Node origNode;
    private final IPathVisitor<Node> visitor;
    private final int maxTraceWidth;
    private final int maxTraceDepth;
    private final List<Node> visited = new ArrayList<Node>();
    // The set of all nodes reachable from origNode
    private final Set<Node> relevant;
    public AllPathsBuilder(IGraph<Node> graph, Node origNode, IPathVisitor<Node> visitor, int maxPathWidth, int maxPathDepth) {
        this.graph = graph;
        this.origNode = origNode;
        this.visitor = visitor;
        this.maxTraceWidth = maxPathWidth;
        this.maxTraceDepth = maxPathDepth;
        relevant = new HashSet<Node>();
        relevant.add(origNode);
        // 'worklist' is needed only to compute 'relevant' and is discarded after that.
        Stack<Node> worklist = new Stack<Node>();
        worklist.add(origNode);
        while (!worklist.isEmpty()) {
            Node currNode = worklist.pop();
            for (Node succNode : graph.getSuccs(currNode)) {
                if (relevant.add(succNode))
                    worklist.add(succNode);
            }
        }
    }
    public List<StringBuffer> getAllPathsTo(Node dstNode) {
        if (relevant.contains(dstNode)) {
            return getAllNonEmptyPathsTo(dstNode);
        }
        return Collections.emptyList();
    }
    private List<StringBuffer> singletonList(String s) {
        List<StringBuffer> list = new ArrayList<StringBuffer>(1);
        list.add(new StringBuffer(s));
        return list;
    }
    private List<StringBuffer> getAllNonEmptyPathsTo(Node currNode) {
        if (currNode.equals(origNode)) {
            return singletonList("");
        }
        int depth = visited.size();
        if (depth >= maxTraceDepth) {
            return singletonList("<truncated depth=\"" + depth + "\"/>");
        }
        Set<Node> preds = graph.getPreds(currNode);
        int width = preds.size();
        if (width > maxTraceWidth) {
            return singletonList("<truncated width=\"" + width + "\" limit=\"" + maxTraceWidth + "\"/>");
        }
        int i = 0;
        List<StringBuffer> newPaths = null;
        visited.add(currNode);
        for (Node predNode : preds) {
            if (!relevant.contains(predNode))
                continue;
            if (visited.contains(predNode))
                continue;
            List<StringBuffer> subPaths = getAllNonEmptyPathsTo(predNode);
            for (StringBuffer subPath : subPaths) {
                String t = visitor.visit(predNode, currNode);
                subPath.append(t);
            }
            if (newPaths == null) {
                if (subPaths.size() > 0)
                    newPaths = subPaths;
            } else
                newPaths.addAll(subPaths);
        }
        if (newPaths == null) {
            newPaths = Collections.emptyList();
        }
        visited.remove(visited.size() - 1);
        return newPaths;
    }
}
