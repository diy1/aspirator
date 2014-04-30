package chord.analyses.thread;

import chord.analyses.method.DomM;
import chord.analyses.alias.CICGAnalysis;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramRel;

/**
 * Call graph analysis producing a thread-sensitive and context-insensitive
 * call graph of the program.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
    name = "thrsen-cicg-java",
    consumes = { "thrSenRootM", "thrSenReachableM", "thrSenIM", "thrSenMM" }
)
public class ThrSenCICGAnalysis extends CICGAnalysis {
    public void run() {
        domM = (DomM) ClassicProject.g().getTrgt("M");
        relRootM = (ProgramRel) ClassicProject.g().getTrgt("thrSenRootM");
        relReachableM = (ProgramRel) ClassicProject.g().getTrgt("thrSenReachableM");
        relIM = (ProgramRel) ClassicProject.g().getTrgt("thrSenIM");
        relMM = (ProgramRel) ClassicProject.g().getTrgt("thrSenMM");
    }
}
