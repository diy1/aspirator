package chord.project;

import java.io.File;
import java.io.IOException;
import chord.util.Utils;

/**
 * System properties recognized by Chord.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class Config {
    private static final String BAD_OPTION = "ERROR: Unknown value '%s' for system property '%s'; expected: %s";

    private Config() { }

    // properties concerning settings of the JVM running Chord

    public final static String maxHeap = System.getProperty("chord.max.heap");
    public final static String maxStack = System.getProperty("chord.max.stack");
    public final static String jvmargs = System.getProperty("chord.jvmargs");

    // basic properties about program being analyzed (its main class, classpath, command line args, etc.)

    public final static String workDirName = System.getProperty("chord.work.dir");
    public final static String mainClassName = System.getProperty("chord.main.class");
    public final static String userClassPathName = System.getProperty("chord.class.path");
    public final static String srcPathName = System.getProperty("chord.src.path");
    public final static String ignoredExceptions = System.getProperty("chord.ignore.exceptions", "java.io.FileNotFoundException");
    public final static String ignoredMethods = System.getProperty("chord.ignore.methods", "close,cleanup,stop,shutdown");
    public final static String runIDs = System.getProperty("chord.run.ids", "0");
    public final static String runtimeJvmargs = System.getProperty("chord.runtime.jvmargs", "-ea -Xmx1024m");

    // properties concerning how the program's analysis scope is constructed

    public final static String scopeKind = System.getProperty("chord.scope.kind", "rta");
    public final static String reflectKind = System.getProperty("chord.reflect.kind", "none");
    public final static String CHkind = System.getProperty("chord.ch.kind", "static");
    public final static String ssaKind = System.getProperty("chord.ssa.kind", "phi");
    static {
        check(CHkind, new String[] { "static", "dynamic" }, "chord.ch.kind");
        check(reflectKind, new String[] { "none", "static", "dynamic", "static_cast" }, "chord.reflect.kind");
        check(ssaKind, new String[] { "none", "phi", "nophi" }, "chord.ssa.kind");
    }
    public final static String DEFAULT_SCOPE_EXCLUDES = "";
    public final static String scopeStdExcludeStr = System.getProperty("chord.std.scope.exclude", DEFAULT_SCOPE_EXCLUDES);
    public final static String scopeExtExcludeStr = System.getProperty("chord.ext.scope.exclude", "");
    public static String scopeExcludeStr =
        System.getProperty("chord.scope.exclude", Utils.concat(scopeStdExcludeStr, ",", scopeExtExcludeStr));
    public final static String DEFAULT_CHECK_EXCLUDES =
        "java.,javax.,sun.,com.sun.,com.ibm.,org.apache.harmony.";
    public final static String checkStdExcludeStr = System.getProperty("chord.std.check.exclude", DEFAULT_CHECK_EXCLUDES);
    public final static String checkExtExcludeStr = System.getProperty("chord.ext.check.exclude", "");
    public final static String checkExcludeStr =
        System.getProperty("chord.check.exclude", Utils.concat(checkStdExcludeStr, ",", checkExtExcludeStr));

    // properties dictating what gets computed/printed by Chord

    public final static boolean buildScope = Utils.buildBoolProperty("chord.build.scope", false);
    public final static String runAnalyses = System.getProperty("chord.run.analyses", "");
    public final static String printClasses = System.getProperty("chord.print.classes", "").replace('#', '$');
    public final static boolean printAllClasses = Utils.buildBoolProperty("chord.print.all.classes", false);
    public final static String printRels = System.getProperty("chord.print.rels", "");
    public final static boolean printProject = Utils.buildBoolProperty("chord.print.project", false);
    public final static boolean printResults = Utils.buildBoolProperty("chord.print.results", true);
    public final static boolean saveDomMaps = Utils.buildBoolProperty("chord.save.maps", true);
    // Determines verbosity level of Chord:
    // 0 => silent
    // 1 => print task/process enter/leave/time messages and sizes of computed doms/rels
    //      bddbddb: print sizes of relations output by solver
    // 2 => all other messages in Chord
    //      bddbddb: print bdd node resizing messages, gc messages, and solver stats (e.g. how long each iteration took)
    // 3 => bddbddb: noisy=yes for solver
    // 4 => bddbddb: tracesolve=yes for solver
    // 5 => bddbddb: fulltravesolve=yes for solver
    public final static int verbose = Integer.getInteger("chord.verbose", 1);

    // Chord project properties

    public final static boolean classic = System.getProperty("chord.classic").equals("true");
    public final static String stdJavaAnalysisPathName = System.getProperty("chord.std.java.analysis.path");
    public final static String extJavaAnalysisPathName = System.getProperty("chord.ext.java.analysis.path");
    public final static String javaAnalysisPathName = System.getProperty("chord.java.analysis.path");
    public final static String stdDlogAnalysisPathName = System.getProperty("chord.std.dlog.analysis.path");
    public final static String extDlogAnalysisPathName = System.getProperty("chord.ext.dlog.analysis.path");
    public final static String dlogAnalysisPathName = System.getProperty("chord.dlog.analysis.path");

    // properties specifying configuration of instrumentation and dynamic analysis

    public final static boolean useJvmti = Utils.buildBoolProperty("chord.use.jvmti", false);
    public final static String instrKind = System.getProperty("chord.instr.kind", "offline");
    public final static String traceKind = System.getProperty("chord.trace.kind", "full");
    public final static int traceBlockSize = Integer.getInteger("chord.trace.block.size", 4096);
    static {
        check(instrKind, new String[] { "offline", "online" }, "chord.instr.kind");
        check(traceKind, new String[] { "full", "pipe" }, "chord.trace.kind");
    }
    public final static boolean dynamicHaltOnErr = Utils.buildBoolProperty("chord.dynamic.haltonerr", true);
    public final static int dynamicTimeout = Integer.getInteger("chord.dynamic.timeout", -1);
    public final static int maxConsSize = Integer.getInteger("chord.max.cons.size", 50000000);

    // properties dictating what is reused across Chord runs

    public final static boolean reuseScope = Utils.buildBoolProperty("chord.reuse.scope", false);
    public final static boolean reuseRels =Utils.buildBoolProperty("chord.reuse.rels", false);
    public final static boolean reuseTraces =Utils.buildBoolProperty("chord.reuse.traces", false);

    // properties concerning BDDs

    public final static boolean useBuddy =Utils.buildBoolProperty("chord.use.buddy", false);
    public final static String bddbddbMaxHeap = System.getProperty("chord.bddbddb.max.heap", "1024m");

    // properties specifying names of Chord's output files and directories

    public static String outDirName = System.getProperty("chord.out.dir", workRel2Abs("chord_output"));
    public final static String outFileName = System.getProperty("chord.out.file", outRel2Abs("log.txt"));
    public final static String errFileName = System.getProperty("chord.err.file", outRel2Abs("log.txt"));    
    public final static String reflectFileName = System.getProperty("chord.reflect.file", outRel2Abs("reflect.txt"));
    public final static String methodsFileName = System.getProperty("chord.methods.file", outRel2Abs("methods.txt"));
    public final static String classesFileName = System.getProperty("chord.classes.file", outRel2Abs("classes.txt"));
    public final static String extraClassesFileName = System.getProperty("chord.extraclasses.file", outRel2Abs("extraclasses.txt")); // Added by Ding
    public final static String bddbddbWorkDirName = System.getProperty("chord.bddbddb.work.dir", outRel2Abs("bddbddb"));
    public final static String bootClassesDirName = System.getProperty("chord.boot.classes.dir", outRel2Abs("boot_classes"));
    public final static String userClassesDirName = System.getProperty("chord.user.classes.dir", outRel2Abs("user_classes"));
    public final static String instrSchemeFileName = System.getProperty("chord.instr.scheme.file", outRel2Abs("scheme.ser"));
    public final static String traceFileName = System.getProperty("chord.trace.file", outRel2Abs("trace"));

    static {
        Utils.mkdirs(outDirName);
        Utils.mkdirs(bddbddbWorkDirName);
    }

    // commonly-used constants

    public final static String mainDirName = System.getProperty("chord.main.dir");
    public final static String javaClassPathName = System.getProperty("java.class.path");
    public final static String toolClassPathName =
        mainDirName + File.separator + "chord.jar" + File.pathSeparator + javaAnalysisPathName;
    public final static String stubsFileName = "chord/program/stubs/stubs.txt";
    // This source of this agent is defined in main/agent/chord_instr_agent.cpp.
    // See the ccompile target in main/build.xml and main/agent/Makefile for how it is built.
    public final static String cInstrAgentFileName = mainDirName + File.separator + "libchord_instr_agent.so";
    // This source of this agent is defined in main/src/chord/instr/OnlineTransformer.java.
    // See the jcompile target in main/build.xml for how it is built.
    public final static String jInstrAgentFileName = mainDirName + File.separator + "chord.jar";
    public final static String javadocURL = "http://chord.stanford.edu/javadoc/";

    public final static String[] scopeExcludeAry = Utils.toArray(scopeExcludeStr);
    public static boolean isExcludedFromScope(String typeName) {
        for (String c : scopeExcludeAry)
            if (typeName.startsWith(c))
                return true;
        return false;
    }
    public final static String[] checkExcludeAry = Utils.toArray(checkExcludeStr);
    public static boolean isExcludedFromCheck(String typeName) {
        for (String c : checkExcludeAry)
            if (typeName.startsWith(c))
                return true;
        return false;
    }

    public static void print() {
        System.out.println("java.vendor: " + System.getProperty("java.vendor"));
        System.out.println("java.version: " + System.getProperty("java.version"));
        System.out.println("os.arch: " + System.getProperty("os.arch"));
        System.out.println("os.name: " + System.getProperty("os.name"));
        System.out.println("os.version: " + System.getProperty("os.version"));
        System.out.println("java.class.path: " + javaClassPathName);
        System.out.println("chord.max.heap: " + maxHeap);
        System.out.println("chord.max.stack: " + maxStack);
        System.out.println("chord.jvmargs: " + jvmargs);
        System.out.println("chord.main.dir: " + mainDirName);
        System.out.println("chord.work.dir: " + workDirName);
        System.out.println("chord.main.class: " + mainClassName);
        System.out.println("chord.class.path: " + userClassPathName);
        System.out.println("chord.src.path: " + srcPathName);
        System.out.println("chord.run.ids: " + runIDs);
        System.out.println("chord.runtime.jvmargs: " + runtimeJvmargs);
        System.out.println("chord.scope.kind: " + scopeKind);
        System.out.println("chord.reflect.kind: " + reflectKind);
        System.out.println("chord.ch.kind: " + CHkind);
        System.out.println("chord.ssa: " + ssaKind);
        System.out.println("chord.std.scope.exclude: " + scopeStdExcludeStr);
        System.out.println("chord.ext.scope.exclude: " + scopeExtExcludeStr);
        System.out.println("chord.scope.exclude: " + scopeExcludeStr);
        System.out.println("chord.std.check.exclude: " + checkStdExcludeStr);
        System.out.println("chord.ext.check.exclude: " + checkExtExcludeStr);
        System.out.println("chord.check.exclude: " + checkExcludeStr);
        System.out.println("chord.build.scope: " + buildScope);
        System.out.println("chord.run.analyses: " + runAnalyses);
        System.out.println("chord.print.all.classes: " + printAllClasses);
        System.out.println("chord.print.classes: " + printClasses);
        System.out.println("chord.print.rels: " + printRels);
        System.out.println("chord.print.project: " + printProject);
        System.out.println("chord.print.results: " + printResults);
        System.out.println("chord.save.maps: " + saveDomMaps);
        System.out.println("chord.verbose: " + verbose);
        System.out.println("chord.classic: " + classic);
        System.out.println("chord.std.java.analysis.path: " + stdJavaAnalysisPathName);
        System.out.println("chord.ext.java.analysis.path: " + extJavaAnalysisPathName);
        System.out.println("chord.java.analysis.path: " + javaAnalysisPathName);
        System.out.println("chord.std.dlog.analysis.path: " + stdDlogAnalysisPathName);
        System.out.println("chord.ext.dlog.analysis.path: " + extDlogAnalysisPathName);
        System.out.println("chord.dlog.analysis.path: " + dlogAnalysisPathName);
        System.out.println("chord.use.jvmti: " + useJvmti);
        System.out.println("chord.instr.kind: " + instrKind);
        System.out.println("chord.trace.kind: " + traceKind);
        System.out.println("chord.trace.block.size: " + traceBlockSize);
        System.out.println("chord.dynamic.haltonerr: " + dynamicHaltOnErr);
        System.out.println("chord.dynamic.timeout: " + dynamicTimeout);
        System.out.println("chord.max.cons.size: " + maxConsSize);
        System.out.println("chord.reuse.scope: " + reuseScope);
        System.out.println("chord.reuse.rels: " + reuseRels);
        System.out.println("chord.reuse.traces: " + reuseTraces);
        System.out.println("chord.use.buddy: " + useBuddy);
        System.out.println("chord.bddbddb.max.heap: " + bddbddbMaxHeap);
    }

    public static String outRel2Abs(String fileName) {
        return (fileName == null) ? null : Utils.getAbsolutePath(outDirName, fileName);
    }

    public static String workRel2Abs(String fileName) {
        return (fileName == null) ? null : Utils.getAbsolutePath(workDirName, fileName);
    }

    public static void check(String val, String[] legalVals, String key) {
        for (String s : legalVals) {
            if (val.equals(s))
                return;
        }
        String legalValsStr = "[ ";
        for (String s : legalVals)
            legalValsStr += s + " ";
        legalValsStr += "]";
        Messages.fatal(BAD_OPTION, val, key, legalValsStr);
    }
}
