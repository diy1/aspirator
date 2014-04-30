package chord.util.graph;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * A visitor over the Strongly Connected Components (SCCs) of a directed graph.
 *  
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class SCCFindingVisitor<Node> implements IGraphEntityVisitor<Node> {
    private final List<Set<Node>> sccList;
    private Set<Node> currSCC;
    public SCCFindingVisitor() {
        sccList = new ArrayList<Set<Node>>();
    }
    public void prologue() {
        currSCC = new HashSet<Node>();
        sccList.add(currSCC);
    }
    public void visit(Node node) {
        currSCC.add(node);
    }
    public void epilogue() {
        // do nothing
    }
    /**
     * Provides all SCCs of the directed graph.
     *
     * @return All SCCs of the directed graph.
     */ 
    public List<Set<Node>> getSCCs() {
        return sccList;
    }
}
