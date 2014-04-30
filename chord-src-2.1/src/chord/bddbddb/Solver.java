package chord.bddbddb;

import java.io.File;
import chord.project.Config;
import chord.project.OutDirUtils;

/**
 * Interface to bddbddb's BDD-based Datalog solver.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class Solver {
    /**
     * Runs bddbddb's BDD-based Datalog solver on the specified Datalog program.
     * <p>
     * The maximum amount of memory available to the solver at run-time can be specified by the user via system property
     * <tt>bddbddb.max.heap.size</tt> (default is 1024m).
     * 
     * @param fileName A file containing a Datalog program.
     */
    public static void run(String fileName) {
        String[] cmdArray = new String[] {
            "java",
            "-ea",
            "-Xmx" + Config.bddbddbMaxHeap,
            "-cp",
            Config.mainDirName + File.separator + "chord.jar",
            "-Dverbose=" + Config.verbose,
            Config.useBuddy ? ("-Djava.library.path=" + Config.mainDirName) : "-Dbdd=j",
            "-Dbasedir=" + Config.bddbddbWorkDirName,
            "net.sf.bddbddb.Solver",
            fileName
        };
        OutDirUtils.executeWithFailOnError(cmdArray);
    }
}
