package chord.project;

import java.io.File;
import java.io.PrintStream;

import chord.program.Program;
import chord.util.Timer;
import chord.util.Utils;

/**
 * Entry point of Chord after JVM settings are resolved.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class Main {
    public static void main(String[] args) throws Exception {
        File outFile;
        {
            String outFileName = Config.outFileName;
            if (outFileName == null)
                outFile = null;
            else {
                outFile = new File(outFileName);
                System.out.println("Redirecting stdout to file: " + outFile);
            }
        }
        File errFile;
        {
            String errFileName = Config.errFileName;
            if (errFileName == null)
                errFile = null;
            else {
                errFile = new File(errFileName);
                System.out.println("Redirecting stderr to file: " + errFile);
            }
        }
        PrintStream outStream = null;
        PrintStream errStream = null;
        if (outFile != null) {
            outStream = new PrintStream(outFile);
            System.setOut(outStream);
        }
        if (errFile != null) {
            if (outFile != null && errFile.equals(outFile))
                errStream = outStream;
            else
                errStream = new PrintStream(errFile);
            System.setErr(errStream);
        }
        run();
        if (outStream != null)
            outStream.close();
        if (errStream != null && errStream != outStream)
            errStream.close();
    }
    private static void run() {
        Timer timer = new Timer("chord");
        timer.init();
        String initTime = timer.getInitTimeStr();
        if (Config.verbose >= 0)
            System.out.println("Chord run initiated at: " + initTime);
        if (Config.verbose >= 2)
            Config.print();
        Program program = Program.g();
        Project project = Project.g();
        if (Config.buildScope) {
            program.build();
        }
        if (Config.printAllClasses)
            program.printAllClasses();
        String[] printClasses = Utils.toArray(Config.printClasses);
        if (printClasses.length > 0) {
            for (String className : printClasses)
                program.printClass(className);
        }
        String[] analysisNames = Utils.toArray(Config.runAnalyses);
        if (analysisNames.length > 0) {
            project.run(analysisNames);
        }
        String[] relNames = Utils.toArray(Config.printRels);
        if (relNames.length > 0) {
            project.printRels(relNames);
        }
        if (Config.printProject) {
            project.print();
        }
        timer.done();
        String doneTime = timer.getDoneTimeStr();
        if (Config.verbose >= 0) {
            System.out.println("Chord run completed at: " + doneTime);
            System.out.println("Total time: " + timer.getInclusiveTimeStr());
        }
    }
}
