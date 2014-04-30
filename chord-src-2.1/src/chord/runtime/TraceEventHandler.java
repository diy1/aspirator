package chord.runtime;

import java.io.IOException;

import chord.instr.InstrScheme;
import chord.util.WeakIdentityHashMap;
import chord.util.ByteBufferedFile;

/**
 * Basic handler of events generated during an instrumented program's
 * execution for use by multi-JVM dynamic analyses.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class TraceEventHandler extends BasicEventHandler {
    public static final String TRACE_BLOCK_SIZE_KEY = "trace_block_size";
    public static final String TRACE_FILE_KEY = "trace_file";
    /**
     * A buffer used to buffer events sent from event-generating JVM to
     * event-handling JVM.
     * It is irrelevant if events are generated/handled by the same JVM.
     */ 
    protected static ByteBufferedFile buffer;
    /**
     * This method is called during handing of JVMTI event "VMInit".
     * arguments: trace_file, trace_block_size
     * if trace_file is absent then buffer is not created (i.e. it is
     * assumed that dynamic analysis is intra-JVM).
     */
    public synchronized static void init(String args) {
        String[] a = args.split("=");
        int traceBlockSize = 4096;
        String traceFileName = null;
        for (int i = 0; i < a.length; i += 2) {
            String k = a[i];
            if (k.equals(TRACE_BLOCK_SIZE_KEY))
                traceBlockSize = Integer.parseInt(a[i+1]);
            else if (k.equals(TRACE_FILE_KEY))
                traceFileName = a[i+1];
        }
        if (traceFileName == null) {
            System.err.println("ERROR: TraceEventHandler: Expected argument " +
                TRACE_FILE_KEY + "=<FILE NAME>");
            System.exit(1);
        }
        try {
            buffer = new ByteBufferedFile(traceBlockSize, traceFileName, false);
        } catch (IOException ex) {
            ex.printStackTrace();
            System.exit(1);
        }
        BasicEventHandler.init(args);
    }

    // called during VMDeath JVMTI event
    public synchronized static void done() {
        BasicEventHandler.done();
        if (buffer != null) {
            try {
                buffer.flush();
            } catch (IOException ex) {
                ex.printStackTrace();
                System.exit(1);
            }
        }
    }
}

