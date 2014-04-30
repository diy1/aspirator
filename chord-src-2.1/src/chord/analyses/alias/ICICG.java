package chord.analyses.alias;

import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;

import chord.util.graph.ILabeledGraph;

/**
 * Specification of a context-insensitive call graph.
 * 
 * @author Mayur Naik <mhn@cs.stanford.edu>
 */
public interface ICICG extends ILabeledGraph<jq_Method, Quad> {
    /**
     * Provides the set of all methods that may be called by a given call site.
     * 
     * @param invk A call site.
     * 
     * @return The set of all methods that may be called by call site <tt>invk</tt>.
     */
    public Set<jq_Method> getTargets(Quad invk);
    /**
     * Provides the set of all call sites that may call a given method.
     * 
     * @param meth A method.
     * 
     * @return The set of all call sites that may call method <tt>meth</tt>.
     */
    public Set<Quad> getCallers(jq_Method meth);
    /**
     * Determines whether a given call site may call a given method.
     * 
     * @param invk A call site.
     * @param meth A method.
     * 
     * @return true iff call site <tt>invk</tt> may call method <tt>meth</tt>.
     */
    public boolean calls(Quad invk, jq_Method meth);
}
