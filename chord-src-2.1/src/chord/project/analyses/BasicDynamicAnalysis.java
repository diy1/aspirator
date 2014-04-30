package chord.project.analyses;

import gnu.trove.set.hash.TIntHashSet;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Stack;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import chord.util.Utils;
import chord.analyses.basicblock.DomB;
import chord.analyses.method.DomM;
import chord.instr.EventKind;
import chord.instr.BasicInstrumentor;
import chord.instr.OfflineTransformer;
import chord.instr.TracePrinter;
import chord.instr.TraceTransformer;
import chord.project.Messages;
import chord.project.Project;
import chord.project.Config;
import chord.project.OutDirUtils;
import chord.runtime.TraceEventHandler;
import chord.runtime.BasicEventHandler;
import chord.util.ByteBufferedFile;
import chord.util.Executor;
import chord.util.ProcessExecutor;
import chord.util.tuple.object.Pair;

/**
 * Generic implementation of a basic dynamic analysis.
 * 
 * Custom dynamic analyses must extend either this class or class
 * {@link chord.project.analyses.DynamicAnalysis}.

 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class BasicDynamicAnalysis extends JavaAnalysis {
    ///// Shorthands for error/warning messages in this class

    private static final String STARTING_RUN = "INFO: BasicDynamicAnalysis: Starting Run ID %s in %s mode.";
    private static final String FINISHED_RUN = "INFO: BasicDynamicAnalysis: Finished Run ID %s in %s mode.";
    private static final String FINISHED_PROCESSING_TRACE =
        "INFO: BasicDynamicAnalysis: Finished processing trace with %d events.";
    private static final String REUSE_ONLY_FULL_TRACES =
        "ERROR: BasicDynamicAnalysis: Can only reuse full traces.";
    private final static String INSTRUMENTOR_ARGS = "INFO: BasicDynamicAnalysis: Instrumentor arguments: %s";
    private final static String EVENTHANDLER_ARGS = "INFO: BasicDynamicAnalysis: Event handler arguments: %s";

    public final static boolean DEBUG = false;

    protected Map<String, String> eventHandlerArgs;
    protected Map<String, String> instrumentorArgs;

    /**
     * Arguments to be passed to the event handler.
     *
     * If this dynamic analysis is multi-JVM (uses separate JVMs for generating
     * and handling events), then the following arguments are passed to the event
     * handler by default:
     * 1. trace_block_size=<SIZE>
     * 2. trace_file=<FILE NAME>
     * 
     * If this dynamic analysis is single-JVM, then no arguments are passed to the
     * event handler by default.
     * 
     * Whether or not this dynamic analysis is single- or multi-JVM is determined
     * by method {@link #getEventHandlerClass()}.  If this method returns a
     * subclass of {@link chord.runtime.TraceEventHandler}, then it is multi-JVM;
     * otherwise, it is single-JVM.
     * 
     * The value of <SIZE> is determined by method {@link #getTraceBlockSize()}.
     * The value of <FILE NAME> is determined by method {@link #getTraceFileName(int)}.
     *
     * Subclasses can override this method but must call
     * <code>super.getEventHandlerArgs()</code>, add any additional arguments to the
     * returned map, and return that same map.
     */
    public Map<String, String> getEventHandlerArgs() {
        if (eventHandlerArgs == null) {
            eventHandlerArgs = new HashMap<String, String>();
            if (useTraces()) {
                int traceBlockSize = getTraceBlockSize();
                String traceFileName = getTraceFileName(getTraceTransformers().size());
                eventHandlerArgs.put(TraceEventHandler.TRACE_BLOCK_SIZE_KEY, Integer.toString(traceBlockSize));
                eventHandlerArgs.put(TraceEventHandler.TRACE_FILE_KEY, traceFileName);
            }
        }
        return eventHandlerArgs;
    }

    /**
     * Determines whether this dynamic analysis is multi-JVM (uses separate JVMs for
     * generating and handling events) or single-JVM.
     *
     * @return true if this dynamic analysis is multi-JVM.
     */
    private boolean useTraces() {
        return Utils.isSubclass(getEventHandlerClass(), TraceEventHandler.class);
    }

    /**
     * Arguments to be passed to the instrumentor.
     *
     * If this dynamic analysis uses the JVMTI agent implemented in main/agent/
     * to start and end the event handler at runtime, then the only argument passed
     * to the instrumentor by default is use_jvmti=true.
     *
     * If this dynamic analysis does not use the JVMTI agent, then the following
     * arguments are passed to the instrumentor by default:
     * 1. use_jvmti=false
     * 2. event_handler_class=<CLASS NAME>
     * 3. event_handler_args=<KEY1>@<VAL1>@ ... @<KEYn>@<VALn>
     * The reason these arguments are passed to the instrumentor is because, in
     * the absence of the JVMTI agent, the instrumentor must inject calls to
     * start and end the event handler at runtime, at the entry and exit,
     * respectively, of the bytecode of the main method of the analyzed program.
     *
     * Whether or not this dynamic analysis uses the JVMTI agent is determined
     * by method {@link #useJvmti()}.
     *
     * The value of <CLASS NAME> above is determined by method {@link #getEventHandlerClass()}.
     * The values of <KEY1>, <VAL1>, ..., <KEYn>, <VALn> above are determined by
     * method {@link #getEventHandlerArgs()}.
     *
     * Subclasses can override this method but must call
     * <code>super.getInstrumentorArgs()</code>, add any additional arguments to the
     * returned map, and return that same map.
     */
    public Map<String, String> getInstrumentorArgs() {
        if (instrumentorArgs == null) {
            instrumentorArgs = new HashMap<String, String>();
            if (useJvmti()) {
                instrumentorArgs.put(BasicInstrumentor.USE_JVMTI_KEY, "true");
            } else {
                instrumentorArgs.put(BasicInstrumentor.USE_JVMTI_KEY, "false");
                String c = getEventHandlerClass().getName();
                Map<String, String> ehArgs = getEventHandlerArgs();
                String a = mapToStr(ehArgs, '@');
                if (a.length() > 0) a = a.substring(1);
                instrumentorArgs.put(BasicInstrumentor.EVENT_HANDLER_CLASS_KEY, c);
                instrumentorArgs.put(BasicInstrumentor.EVENT_HANDLER_ARGS_KEY, a);
            }
        }
        return instrumentorArgs;
    }

    /**
     * The class of the instrumentor to be used.
     *
     * Subclasses can override this method but must return a class which is a
     * subclass of {@link chord.instr.BasicInstrumentor}.
     */
    public Class getInstrumentorClass() {
        return BasicInstrumentor.class;
    }

    /**
     * The class of the event handler to be used.
     *
     * Subclasses can override this method but must return a class which extends
     * {@link chord.runtime.BasicEventHandler}.  Additionally, if the dynamic
     * analysis implemented by the subclass is multi-JVM (uses separate JVMs for
     * generating and handling events), then this method must return a class
     * which extends {@link chord.runtime.TraceEventHandler}.
     */
    public Class getEventHandlerClass() {
        return BasicEventHandler.class;
    }

    /**
     * Determines whether or not this dynamic analysis must use the JVMTI agent
     * implemented in main/agent/ in order to start and end the event handler
     * at runtime.
     *
     * If any dynamic analysis uses this JVMTI agent, then Chord must have been
     * compiled by setting chord.use.jvmti=true (default is false) either on
     * the command line or in file main/chord.properties.
     *
     * Subclasses can override this method.
     */
    public boolean useJvmti() {
        return Config.useJvmti;
    }

    /**
     * Subclasses can override this method.
     */
    public List<Runnable> getTraceTransformers() {
        return Collections.EMPTY_LIST;
    }

    /**
     * Subclasses can override this method.
     */
    public String getInstrKind() {
        return Config.instrKind;
    }

    /**
     * Subclasses can override this method.
     */
    public String getTraceKind() {
        return Config.traceKind;
    }

    /**
     * Subclasses can override this method.
     */
    public boolean haltOnErr() {
        return Config.dynamicHaltOnErr;
    }

    /**
     * Subclasses can override this method.
     */
    public int getTimeout() {
        return Config.dynamicTimeout;
    }

    /**
     * Subclasses can override this method.
     */
    public int getTraceBlockSize() {
        return Config.traceBlockSize;
    }

    /**
     * Subclasses can override this method.
     */
    public boolean reuseTraces() {
        return Config.reuseTraces;
    }

    /**
     * Subclasses can override this method.
     */
    public void initPass() {
    }

    /**
     * Subclasses can override this method.
     */
    public void donePass() {
    }

    /**
     * Subclasses can override this method.
     */
    public void initAllPasses() {
    }

    /**
     * Subclasses can override this method.
     */
    public void doneAllPasses() {
    }

    // provides name of regular (i.e. non-pipe) file to store entire trace
    // provides name of POSIX pipe file to store streaming trace as it is
    // written/read by event generating/processing JVMs

    protected String getTraceFileName(String base, int version, String runID) {
        return base + "_ver" + version + "_run" + runID + ".txt";
    }

    protected String getTraceFileName(String base, int version) {
        return base + "_ver" + version + ".txt";
    }

    // version == 0 means final trace file
    protected String getTraceFileName(int version) {
        return getTraceKind().equals("pipe") ?
            getTraceFileName(Config.traceFileName + "_pipe", version) :
            getTraceFileName(Config.traceFileName + "_full", version);
    }

    protected String getTraceFileName(int version, String runID) {
        return getTraceKind().equals("pipe") ?
            getTraceFileName(Config.traceFileName + "_pipe", version, runID) :
            getTraceFileName(Config.traceFileName + "_full", version, runID);
    }

    protected String[] runIDs = Config.runIDs.split(Utils.LIST_SEPARATOR);

    public boolean canReuseTraces() {
        boolean reuse = false;
        if (reuseTraces()) {
            // check if all trace files from a previous run of
            // Chord exist; only then can those files be reused
            boolean failed = false;
            for (String runID : runIDs) {
                String s = getTraceFileName(0, runID);
                if (!Utils.exists(s)) {
                    failed = true;
                    break;
                }
            }
            if (!failed)
                reuse = true;
        }
        return reuse;
    }

    public void run() {
        String traceKind = getTraceKind();
        if (reuseTraces() && !traceKind.equals("full"))
            Messages.fatal(REUSE_ONLY_FULL_TRACES);
        if (canReuseTraces()) {
            initAllPasses();
            for (String runID : runIDs) {
                if (Config.verbose >= 1) Messages.log(STARTING_RUN, runID, "reuse");
                String s = getTraceFileName(0, runID);
                processTrace(s);
                if (Config.verbose >= 1) Messages.log(FINISHED_RUN, runID, "reuse");
            }
            doneAllPasses();
            return;
        }
        boolean offline = getInstrKind().equals("offline");
        boolean useJvmti = useJvmti();
        if (offline)
            doOfflineInstrumentation();
        if (!useTraces()) {
            String msg = "single-JVM " + (offline ? "offline" : "online") + "-instrumentation " +
                (useJvmti ? "JVMTI-based" : "non-JVMTI");
            List<String> basecmd = getBaseCmd(!offline, useJvmti, 0);
            initAllPasses();
            for (String runID : runIDs) {
                String args = System.getProperty("chord.args." + runID, "");
                List<String> fullcmd = new ArrayList<String>(basecmd);
                fullcmd.addAll(Utils.tokenize(args));
                if (Config.verbose >= 1) Messages.log(STARTING_RUN, runID, msg);
                initPass();
                runInstrProgram(fullcmd);
                donePass();
                if (Config.verbose >= 1) Messages.log(FINISHED_RUN, runID, msg);
            }
            doneAllPasses();
            return;
        }
        boolean pipeTraces = traceKind.equals("pipe");
        List<Runnable> transformers = getTraceTransformers();
        int numTransformers = transformers == null ? 0 : transformers.size();
        List<String> basecmd = getBaseCmd(!offline, useJvmti, numTransformers);
        initAllPasses();
        for (String runID : runIDs) {
            if (pipeTraces) {
                for (int i = 0; i < numTransformers + 1; i++) {
                    Utils.deleteFile(getTraceFileName(i));
                    String[] cmd = new String[] { "mkfifo", getTraceFileName(i) };
                    OutDirUtils.executeWithFailOnError(cmd);
                }
            }
            Runnable traceProcessor = new Runnable() {
                public void run() {
                    processTrace(getTraceFileName(0));
                }
            };
            Executor executor = new Executor(!pipeTraces);
            String args = System.getProperty("chord.args." + runID, "");
            final List<String> fullcmd = new ArrayList<String>(basecmd);
            fullcmd.addAll(Utils.tokenize(args));
            Runnable instrProgram = new Runnable() {
                public void run() {
                    runInstrProgram(fullcmd);
                }
            };
            String msg = "multi-JVM " + (pipeTraces ? "POSIX-pipe " : "regular-file ") +
                (offline ? "offline" : "online") + "-instrumentation " +
                (useJvmti ? "JVMTI-based" : "non-JVMTI");
            if (Config.verbose >= 1) Messages.log(STARTING_RUN, runID, msg);
            executor.execute(instrProgram);
            if (transformers != null) {
                for (Runnable r : transformers)
                    executor.execute(r);
            }
            executor.execute(traceProcessor);
            try {
                executor.waitForCompletion();
            } catch (InterruptedException ex) {
                Messages.fatal(ex);
            }
            if (reuseTraces()) {
                String src = getTraceFileName(0);
                String dst = getTraceFileName(0, runID);
                String[] cmd = new String[] { "mv", src, dst };
                OutDirUtils.executeWithFailOnError(cmd);
            }
            if (Config.verbose >= 1) Messages.log(FINISHED_RUN, runID, msg);
        }
        doneAllPasses();
    }

    private void doOfflineInstrumentation() {
        Class instrClass = getInstrumentorClass();
        Map<String, String> instrArgs = getInstrumentorArgs();
        BasicInstrumentor instr = null;
        Exception ex = null;
        try {
            Constructor c = instrClass.getConstructor(new Class[] { Map.class });
            Object o = c.newInstance(new Object[] { instrArgs });
            instr = (BasicInstrumentor) o;
        } catch (InstantiationException e) {
            ex = e;
        } catch (NoSuchMethodException e) {
            ex = e;
        } catch (InvocationTargetException e) {
            ex = e;
        } catch (IllegalAccessException e) {
            ex = e;
        }
        if (ex != null)
            Messages.fatal(ex);
        OfflineTransformer transformer = new OfflineTransformer(instr);
        transformer.run();
    }

    private void runInstrProgram(List<String> cmdList) {
        int timeout = getTimeout();
        boolean haltOnErr = haltOnErr();
        
        String runBefore = System.getProperty("chord.dynamic.runBeforeCmd");
        
        try {
            Process beforeProc = null;
            if(runBefore != null)
                beforeProc = ProcessExecutor.executeAsynch(new String[] { runBefore }, null, null);
            
            if (haltOnErr)
                OutDirUtils.executeWithFailOnError(cmdList);
            else
                OutDirUtils.executeWithWarnOnError(cmdList, timeout);
            
            if(beforeProc != null)
                beforeProc.destroy();
        } catch(Throwable t) { //just log exceptions
            t.printStackTrace();
        }
    }

    private List<String> getBaseCmd(boolean isOnline, boolean useJvmti, int numTransformers) {
        String mainClassName = Config.mainClassName;
        assert (mainClassName != null);
        String classPathName = Config.userClassPathName;
        assert (classPathName != null);
        List<String> basecmd = new ArrayList<String>();
        basecmd.add("java");
        String jvmArgs = Config.runtimeJvmargs;
        basecmd.addAll(Utils.tokenize(jvmArgs));
        basecmd.add("-Xverify:none");
        if (isOnline) {
            Properties props = System.getProperties();
            for (Map.Entry e : props.entrySet()) {
                String key = (String) e.getKey();
                if (key.startsWith("chord."))
                    basecmd.add("-D" + key + "=" + e.getValue());
            }
            basecmd.add("-Xbootclasspath/p:" + Config.toolClassPathName);
            basecmd.add("-cp");
            basecmd.add(classPathName);
        } else {
            String bootClassesDirName = Config.bootClassesDirName;
            String userClassesDirName = Config.userClassesDirName;
            basecmd.add("-Xbootclasspath/p:" + Config.toolClassPathName +
                File.pathSeparator + bootClassesDirName);
            basecmd.add("-cp");
            basecmd.add(userClassesDirName + File.pathSeparator + classPathName);
        }
        if (useJvmti) {
            String name = getEventHandlerClass().getName().replace('.', '/');
            String args = mapToStr(getEventHandlerArgs(), '=');
            String cAgentArgs = "=" + BasicInstrumentor.EVENT_HANDLER_CLASS_KEY +
                "=" + name + args;
            basecmd.add("-agentpath:" + Config.cInstrAgentFileName + cAgentArgs);
        }
        if (isOnline) {
            String name = getInstrumentorClass().getName().replace('.', '/');
            String args = mapToStr(getInstrumentorArgs(), '=');
            String jAgentArgs = "=" + BasicInstrumentor.INSTRUMENTOR_CLASS_KEY +
                "=" + name + args;
            basecmd.add("-javaagent:" + Config.jInstrAgentFileName + jAgentArgs);
        }
        basecmd.add(mainClassName);
        return basecmd;
    }

    private static String mapToStr(Map<String, String> map, char sep) {
        String s = "";
        for (Map.Entry<String, String> e : map.entrySet()) {
            s += sep + e.getKey() + sep + e.getValue();
        }
        return s;
    }

    public void processTrace(String fileName) {
        try {
            initPass();
            ByteBufferedFile buffer = new ByteBufferedFile(
                getTraceBlockSize(), fileName, true);
            long count = 0;
            while (!buffer.isDone()) {
                handleEvent(buffer);
                ++count;
            }
            donePass();
            if (Config.verbose >= 1) Messages.log(FINISHED_PROCESSING_TRACE, count);
        } catch (IOException ex) {
            Messages.fatal(ex);
        }
    }

    public void handleEvent(ByteBufferedFile buffer) throws IOException {
        throw new RuntimeException();
    }
}

/*
            if (Config.verbose >= 2) {
                String argsStr = "";
                for (Map.Entry<String, String> e : eventHandlerArgs.entrySet())
                    argsStr += "\n\t[" + e.getKey() + " = " + e.getValue() + "]";
                Messages.log(EVENTHANDLER_ARGS, argsStr);
            }
            if (Config.verbose >= 2) {
                String argsStr = "";
                for (Map.Entry<String, String> e : instrumentorArgs.entrySet())
                    argsStr += "\n\t[" + e.getKey() + " = " + e.getValue() + "]";
                Messages.log(INSTRUMENTOR_ARGS, argsStr);
            }
*/
