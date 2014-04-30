package chord.project.analyses.rhs;

/**
 * Specification of a path edge or a summary edge in the Reps-Horwitz-Sagiv
 * algorithm for context-sensitive dataflow analysis.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public interface IEdge {
    /**
     * Determines whether the given PE or SE can merge with this edge.
     * This function is always called for a pair of PEs incoming into the same
     * program point, or a pair of SEs for the same method.
     * The two edges can merge iff one of the following holds:
     * <ol> 
     * <li>RHSAnalysis.mustMerge returns true</li>
     * <li>RHSAnalysis.mustMerge returns false but RHSAnalysis.mayMerge returns
     * true; the source nodes of the two edges are identical; one of the target
     * node subsumes another</li>
     * </ol>
     *
     * @param edge A PE or SE.
     *
     * @return <ol>
     * <li>-1 if these two edges cannot be merged</li>
     * <li>0 if these two edges are identical</li>
     * <li>1 if these two edges can be merged, and the return value would be identical to this.</li>
     * <li>2 if these two edges can be merged, and the return value would be identical to the parameter.</li>
     * <li>3 if these two edges can be merged, but there is no info about the return value.</li>
     * </ol>
     */
    public int canMerge(IEdge edge, boolean mustMerge);

    /**
     * Merges the given PE or SE with this edge. This edge is mutated but the
     * given edge is not.  This function is called only if canMerge returns a value >= 0.
     * The source nodes of this edge and the given edge are assumed to be identical.
     * 
     * @param edge A PE or SE.
     * 
     * @return true iff this edge changes due to the merge.
     */
    public boolean mergeWith(IEdge edge);
}
