package chord.runtime;

import java.io.IOException;

import chord.instr.InstrScheme;
import chord.util.WeakIdentityHashMap;
import chord.util.ByteBufferedFile;

/**
 * Basic handler of events generated during an instrumented program's
 * execution for use by single-JVM dynamic analyses.
 * 
 * Methods {@link #init(String)} and {@link #done()} are called when
 * event handling starts and ends, respectively, at runtime.  Who calls
 * these methods depends upon whether or not the dynamic analysis using
 * this event handler uses JVMTI
 * (see {@link chord.project.analyses.BasicDynamicAnalysis#useJvmti()}):
 *
 * - If it uses JVMTI, then these methods are called from the JVMTI agent
 *   implemented in main/agent/; see that directory for more details.
 * - If it does not use JVMTI, then calls to these methods are injected
 *   by the instrumentor at the entry and exit of the bytecode of the
 *   main method of the analyzed program; see the constructor of class
 *  {@link chord.instr.BasicInstrumentor} for more details.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class BasicEventHandler {
    /**
     * Flag determining when it is safe to start handling events at runtime.
     * It is false when the JVM starts.  It is set to true in the
     * {@link #init(String)} method which is called by the handler for the
     * JVMTI event "VMInit" (see file main/src/agent/chord_instr_agent.cpp
     * for the definition of this handler).
     */
    protected static boolean trace = false;
    /**
     * Unique ID given to each object created at runtime.
     * ID 0 is reserved for null and ID 1 is reserved for the hypothetical
     * lone object of a hypothetical class all of whose instance fields
     * are static fields in other real classes.
     */
    protected static int currentId = 2;

    protected static WeakIdentityHashMap objmap;

    // Note: CALLER MUST SYNCHRONIZE!
    public static int getObjectId(Object o) {
        if (o == null)
            return 0;
        Object val = objmap.get(o);
        if (val == null) {
            val = currentId++;
            objmap.put(o, val);
        }
        return (Integer) val;
    }

    public static long getPrimitiveId(int oId, int fId) {
        // We must add 1 below so that we never assign to a field an
        // identifier smaller than (1 << 32).
        long l = oId + 1;
        l = l << 32;
        return l + fId;
    }

    /**
     * Method signaling the start of event handling by a dynamic analysis.
     *
     * See the documentation of this class for more details.
     */
    public synchronized static void init(String args) {
        objmap = new WeakIdentityHashMap();
        trace = true;
    }

    /**
     * Method signaling the end of event handling by a dynamic analysis.
     *
     * See the documentation of this class for more details.
     */
    public synchronized static void done() {
        trace = false;
    }
}

