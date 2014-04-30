package chord.analyses.alias;

import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.Invoke;

import chord.util.ArraySet;
import chord.bddbddb.Rel.RelView;
import chord.analyses.method.DomM;
import chord.project.analyses.ProgramRel;
import chord.util.SetUtils;
import chord.util.graph.AbstractGraph;
import chord.util.tuple.object.Pair;

/**
 * Implementation of a context-sensitive call graph.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class CSCG extends AbstractGraph<Pair<Ctxt, jq_Method>> implements ICSCG {
    protected DomM domM;
    protected ProgramRel relRootCM;
    protected ProgramRel relReachableCM;
    protected ProgramRel relCICM;
    protected ProgramRel relCMCM;
    public CSCG(DomM domM, ProgramRel relRootCM, ProgramRel relReachableCM,
            ProgramRel relCICM, ProgramRel relCMCM) {
        this.domM = domM;
        this.relRootCM = relRootCM;
        this.relReachableCM = relReachableCM;
        this.relCICM = relCICM;
        this.relCMCM = relCMCM;
    }
    public Set<Pair<Ctxt, jq_Method>> getNodes() {
        if (!relReachableCM.isOpen())
            relReachableCM.load();
        Iterable<Pair<Ctxt, jq_Method>> res = relReachableCM.getAry2ValTuples();
        return SetUtils.iterableToSet(res, relReachableCM.size());
    }
    public Set<Pair<Ctxt, jq_Method>> getRoots() {
        if (!relRootCM.isOpen())
            relRootCM.load();
        Iterable<Pair<Ctxt, jq_Method>> res = relRootCM.getAry2ValTuples();
        return SetUtils.iterableToSet(res, relRootCM.size());
    }
    public Set<Pair<Ctxt, jq_Method>> getPreds(Pair<Ctxt, jq_Method> cm) {
        if (!relCMCM.isOpen())
            relCMCM.load();
        RelView view = relCMCM.getView();
        view.selectAndDelete(2, cm.val0);
        view.selectAndDelete(3, cm.val1);
        Iterable<Pair<Ctxt, jq_Method>> res = view.getAry2ValTuples();
        return SetUtils.iterableToSet(res, view.size());
    }
    public Set<Pair<Ctxt, jq_Method>> getSuccs(Pair<Ctxt, jq_Method> cm) {
        if (!relCMCM.isOpen())
            relCMCM.load();
        RelView view = relCMCM.getView();
        view.selectAndDelete(0, cm.val0);
        view.selectAndDelete(1, cm.val1);
        Iterable<Pair<Ctxt, jq_Method>> res = view.getAry2ValTuples();
        return SetUtils.iterableToSet(res, view.size());
    }
    public boolean hasNode(Pair<Ctxt, jq_Method> node) {
        if (!relReachableCM.isOpen())
            relReachableCM.load();
        return relReachableCM.contains(node.val0, node.val1);
    }
    public boolean hasRoot(Pair<Ctxt, jq_Method> node) {
        if (!relRootCM.isOpen())
            relRootCM.load();
        if (relRootCM.contains(node.val0, node.val1))
            return true;
        return false;
    }
    public int numSuccs(Pair<Ctxt, jq_Method> node) {
        if (!relCMCM.isOpen())
            relCMCM.load();
        RelView view = relCMCM.getView();
        view.selectAndDelete(0, node.val0);
        view.selectAndDelete(1, node.val1);
        return view.size();
    }
    public Set<Ctxt> getContexts(jq_Method jq_Method) {
        if (!relReachableCM.isOpen())
            relReachableCM.load();
        RelView view = relReachableCM.getView();
        view.selectAndDelete(1, jq_Method);
        Iterable<Ctxt> res = view.getAry1ValTuples();
        Set<Ctxt> ctxts = SetUtils.newSet(view.size());
        for (Ctxt ctxt : res)
            ctxts.add(ctxt);
        return ctxts;
    }
    public Set<Pair<Ctxt, Quad>> getCallers(Ctxt ctxt, jq_Method meth) {
        if (!relCICM.isOpen())
            relCICM.load();
        RelView view = relCICM.getView();
        view.selectAndDelete(2, ctxt);
        view.selectAndDelete(3, meth);
        Iterable<Pair<Ctxt, Quad>> res = view.getAry2ValTuples();
        Set<Pair<Ctxt, Quad>> CIs = SetUtils.newSet(view.size());
        for (Pair<Ctxt, Quad> ci : res)
            CIs.add(ci);
        return CIs;
    }
    public Set<Pair<Ctxt, jq_Method>> getTargets(Ctxt ctxt, Quad invk) {
        if (!relCICM.isOpen())
            relCICM.load();
        RelView view = relCICM.getView();
        view.selectAndDelete(0, ctxt);
        view.selectAndDelete(1, invk);
        Iterable<Pair<Ctxt, jq_Method>> res = view.getAry2ValTuples();
        Set<Pair<Ctxt, jq_Method>> CMs = SetUtils.newSet(view.size());
        for (Pair<Ctxt, jq_Method> cm : res)
            CMs.add(cm);
        return CMs;
    }
    public Set<Quad> getLabels(Pair<Ctxt, jq_Method> origNode, Pair<Ctxt, jq_Method> destNode) {
        jq_Method meth1 = origNode.val1;
        Set<Quad> invks = new ArraySet<Quad>();
        ControlFlowGraph cfg = meth1.getCFG();
        Ctxt ctxt1 = origNode.val0;
        jq_Method meth2 = destNode.val1;
        Ctxt ctxt2 = destNode.val0;
        for (BasicBlock bb : cfg.reversePostOrder()) {
            for (Quad q : bb.getQuads()) {
                Operator op = q.getOperator();
                if (op instanceof Invoke && calls(ctxt1, q, ctxt2, meth2))
                    invks.add(q);
            }
        }
        return invks;
    }
    public boolean hasEdge(Pair<Ctxt, jq_Method> node1, Pair<Ctxt, jq_Method> node2) {
        if (!relCMCM.isOpen())
            relCMCM.load();
        return relCMCM.contains(node1.val0, node1.val1, node2.val0, node2.val1);
    }
    public int numRoots() {
        if (!relRootCM.isOpen())
            relRootCM.load();
        return relRootCM.size();
    }
    public int numNodes() {
        if (!relReachableCM.isOpen())
            relReachableCM.load();
        return relReachableCM.size();
    }
    public int numPreds(Pair<Ctxt, jq_Method> node) {
        if (!relCMCM.isOpen())
            relCMCM.load();
        RelView view = relCMCM.getView();
        view.selectAndDelete(2, node.val0);
        view.selectAndDelete(3, node.val1);
        return view.size();
    }
    public boolean calls(Ctxt origCtxt, Quad origInvk, Ctxt destCtxt, jq_Method destMeth) {
        if (!relCICM.isOpen())
            relCICM.load();
        return relCICM.contains(origCtxt, origInvk, destCtxt, destMeth);
    }
    /**
     * Frees relations used by this call graph if they are in memory.
     * <p>
     * This jq_Method must be called after clients are done exercising
     * the interface of this call graph.
     */
    public void free() {
        if (relRootCM.isOpen())
            relRootCM.close();
        if (relReachableCM.isOpen())
            relReachableCM.close();
        if (relCICM.isOpen())
            relCICM.close();
        if (relCMCM.isOpen())
            relCMCM.close();
    }
}
