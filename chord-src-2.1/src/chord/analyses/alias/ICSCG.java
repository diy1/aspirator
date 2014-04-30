package chord.analyses.alias;

import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;

import chord.util.graph.ILabeledGraph;
import chord.util.tuple.object.Pair;

/**
 * Specification of a context-sensitive call graph.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public interface ICSCG extends ILabeledGraph<Pair<Ctxt, jq_Method>, Quad> {
    /**
     * Provides the set of all abstract contexts in which a given method may be reachable.
     * 
     * @param meth A method.
     * 
     * @return The set of all abstract contexts in which method <tt>meth</tt> may be reachable.
     */
    public Set<Ctxt> getContexts(jq_Method meth);
    /**
     * Provides the set containing each method along with each abstract context in which it may be called by a given call site in a given abstract context.
     * 
     * @param ctxt An abstract context.
     * @param invk A call site.
     * 
     * @return All (<tt>ctxt2</tt>, <tt>meth</tt>) pairs such that method <tt>meth</tt> may be called in abstract context <tt>ctxt2</tt> from method invocation site
     * <tt>invk</tt> in abstract context <tt>ctxt</tt>.
     */
    public Set<Pair<Ctxt, jq_Method>> getTargets(Ctxt ctxt, Quad invk);
    /**
     * Provides each method invocation site along with each abstract context from which it may call a given method in a given abstract context.
     * 
     * @param ctxt An abstract context.
     * @param meth A method.
     * 
     * @return All (<tt>ctxt2</tt>, <tt>invk</tt>) pairs such that method invocation site <tt>invk</tt> in abstract context <tt>ctxt2</tt> may call method <tt>meth</tt>
     * in abstract context <tt>ctxt</tt>.
     */
    public Set<Pair<Ctxt, Quad>>
        getCallers(Ctxt ctxt, jq_Method meth);
    /**
    /**
     * Determines whether a given method invocation site in a given abstract context may call a given method in a given abstract context.
     * 
     * @param ctxt1 An abstract context.
     * @param invk  A method invocation site.
     * @param ctxt2 An abstract context.
     * @param meth  A method.
     * 
     * @return true iff method invocation site <tt>invk</tt> in abstract context <tt>ctxt1</tt> may call method <tt>meth</tt> in abstract context <tt>ctxt2</tt>.
     */
    public boolean calls(Ctxt ctxt1, Quad invk, Ctxt ctxt2, jq_Method meth);
}
