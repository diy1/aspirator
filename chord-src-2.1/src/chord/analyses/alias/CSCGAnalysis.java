package chord.analyses.alias;

import chord.analyses.method.DomM;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;

/**
 * Context-sensitive call graph analysis.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "cscg-java",
    consumes = { "CICM", "CMCM", "rootCM", "reachableM" }
)
public class CSCGAnalysis extends JavaAnalysis {
    protected DomM domM;
    protected ProgramRel relCICM;
    protected ProgramRel relCMCM;
    protected ProgramRel relRootCM;
    protected ProgramRel relReachableCM;
    protected CSCG callGraph;
    public void run() {
        domM = (DomM) ClassicProject.g().getTrgt("M");
        relRootCM = (ProgramRel) ClassicProject.g().getTrgt("rootCM");
        relReachableCM = (ProgramRel) ClassicProject.g().getTrgt("reachableCM");
        relCICM = (ProgramRel) ClassicProject.g().getTrgt("CICM");
        relCMCM = (ProgramRel) ClassicProject.g().getTrgt("CMCM");
    }
    /**
     * Provides the program's context-sensitive call graph.
     * 
     * @return    The program's context-sensitive call graph.
     */
    public ICSCG getCallGraph() {
        if (callGraph == null) {
            callGraph = new CSCG(domM, relRootCM, relReachableCM,
                relCICM, relCMCM);
        }
        return callGraph;
    }
    /**
     * Frees relations used by this program analysis if they are in
     * memory.
     * <p>
     * This method must be called after clients are done exercising
     * the interface of this analysis.
     */
    public void free() {
        if (callGraph != null)
            callGraph.free();
    }
}
   
