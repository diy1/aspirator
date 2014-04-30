package chord.runtime;

import java.io.IOException;

import chord.instr.EventKind;
import chord.instr.InstrScheme;
import chord.instr.InstrScheme.EventFormat;

/**
 * Buffered file-based offline handler of events generated during an
 * instrumented program's execution.
 * <p>
 * A file is used to communicate events between the JVM running the
 * instrumented program (which produces events) and the JVM running
 * the dynamic program analysis (which consumes events).
 *
 * This handler should suffice for offline dynamic program analyses
 * (i.e. those that handle the events in a separate JVM, either during
 * or after the instrumented program's execution, depending upon
 * whether the value of system property <tt>chord.trace.pipe</tt> is
 * true or false, respectively).
 * 
 * Online analyses (i.e. those that handle events during the
 * instrumented program's execution in the same JVM) must subclass this
 * class and define the relevant event handling methods, i.e. static
 * methods named ".*Event", e.g. {@link #acquireLockEvent(int, Object)}.
 * Which methods are relevant depends upon the instrumentation scheme
 * chosen by the dynamic program analysis;
 * see {@link chord.project.analyses.DynamicAnalysis#getInstrScheme()}.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class EventHandler extends TraceEventHandler {
    public static final int MISSING_FIELD_VAL = -1;
    public static final int UNKNOWN_FIELD_VAL = -2;
    protected static InstrScheme scheme;

    public synchronized static void enterMainMethodEvent() {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.ENTER_MAIN_METHOD);
                buffer.putByte(EventKind.ENTER_MAIN_METHOD);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void enterMethodEvent(int mId) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.ENTER_METHOD);
                buffer.putByte(EventKind.ENTER_METHOD);
                if (mId != MISSING_FIELD_VAL)
                    buffer.putInt(mId);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void leaveMethodEvent(int mId) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.LEAVE_METHOD);
                buffer.putByte(EventKind.LEAVE_METHOD);
                if (mId != MISSING_FIELD_VAL)
                    buffer.putInt(mId);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void basicBlockEvent(int bId) {
        if (trace) {
            trace = false;
            try {
                buffer.putByte(EventKind.BASIC_BLOCK);
                buffer.putInt(bId);
                int tId = getObjectId(Thread.currentThread());
                buffer.putInt(tId);
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void quadEvent(int pId) {
        if (trace) {
            trace = false;
            try {
                buffer.putByte(EventKind.QUAD);
                buffer.putInt(pId);
                int tId = getObjectId(Thread.currentThread());
                buffer.putInt(tId);
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void befMethodCallEvent(int iId, Object o) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.BEF_METHOD_CALL);
                buffer.putByte(EventKind.BEF_METHOD_CALL);
                if (iId != MISSING_FIELD_VAL)
                    buffer.putInt(iId);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
                if (ef.hasObj()) {
                    int oId = getObjectId(o);
                    buffer.putInt(oId);
                }
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void aftMethodCallEvent(int iId, Object o) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.AFT_METHOD_CALL);
                buffer.putByte(EventKind.AFT_METHOD_CALL);
                if (iId != MISSING_FIELD_VAL)
                    buffer.putInt(iId);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
                if (ef.hasObj()) {
                    int oId = getObjectId(o);
                    buffer.putInt(oId);
                }
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void befNewEvent(int hId) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.BEF_NEW);
                buffer.putByte(EventKind.BEF_NEW);
                if (hId != MISSING_FIELD_VAL)
                    buffer.putInt(hId);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
                if (ef.hasObj())
                    buffer.putInt(UNKNOWN_FIELD_VAL);
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void aftNewEvent(int hId, Object o) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.AFT_NEW);
                buffer.putByte(EventKind.AFT_NEW);
                if (hId != MISSING_FIELD_VAL)
                    buffer.putInt(hId);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
                if (ef.hasObj()) {
                    int oId = getObjectId(o);
                    buffer.putInt(oId);
                }
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void newArrayEvent(int hId, Object o) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.NEWARRAY);
                buffer.putByte(EventKind.NEWARRAY);
                if (hId != MISSING_FIELD_VAL)
                    buffer.putInt(hId);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
                if (ef.hasObj()) {
                    int oId = getObjectId(o);
                    buffer.putInt(oId);
                }
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void getstaticPrimitiveEvent(int eId, Object b, int fId) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.GETSTATIC_PRIMITIVE);
                buffer.putByte(EventKind.GETSTATIC_PRIMITIVE);
                if (eId != MISSING_FIELD_VAL)
                    buffer.putInt(eId);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                buffer.putInt(tId);
                }
                if (ef.hasBaseObj()) {
                    int bId = getObjectId(b);
                    buffer.putInt(bId);
                }
                if (fId != MISSING_FIELD_VAL)
                    buffer.putInt(fId);
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void getstaticReferenceEvent(int eId, Object b, int fId, Object o) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.GETSTATIC_REFERENCE);
                buffer.putByte(EventKind.GETSTATIC_REFERENCE);
                if (eId != MISSING_FIELD_VAL)
                    buffer.putInt(eId);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
                if (ef.hasBaseObj()) {
                    int bId = getObjectId(b);
                    buffer.putInt(bId);
                }
                if (fId != MISSING_FIELD_VAL)
                    buffer.putInt(fId);
                if (ef.hasObj()) {
                    int oId = getObjectId(o);
                    buffer.putInt(oId);
                }
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void putstaticPrimitiveEvent(int eId, Object b, int fId) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.PUTSTATIC_PRIMITIVE);
                buffer.putByte(EventKind.PUTSTATIC_PRIMITIVE);
                if (eId != MISSING_FIELD_VAL)
                    buffer.putInt(eId);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
                if (ef.hasBaseObj()) {
                    int bId = getObjectId(b);
                    buffer.putInt(bId);
                }
                if (fId != MISSING_FIELD_VAL)
                    buffer.putInt(fId);
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void putstaticReferenceEvent(int eId, Object b, int fId, Object o) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.PUTSTATIC_REFERENCE);
                buffer.putByte(EventKind.PUTSTATIC_REFERENCE);
                if (eId != MISSING_FIELD_VAL)
                    buffer.putInt(eId);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
                if (ef.hasBaseObj()) {
                    int bId = getObjectId(b);
                    buffer.putInt(bId);
                }
                if (fId != MISSING_FIELD_VAL)
                    buffer.putInt(fId);
                if (ef.hasObj()) {
                    int oId = getObjectId(o);
                    buffer.putInt(oId);
                }
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void getfieldPrimitiveEvent(int eId, Object b, int fId) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.GETFIELD_PRIMITIVE);
                buffer.putByte(EventKind.GETFIELD_PRIMITIVE);
                if (eId != MISSING_FIELD_VAL)
                    buffer.putInt(eId);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
                if (ef.hasBaseObj()) {
                    int bId = getObjectId(b);
                    buffer.putInt(bId);
                }
                if (fId != MISSING_FIELD_VAL)
                    buffer.putInt(fId);
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void getfieldReferenceEvent(int eId, Object b, int fId, Object o) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.GETFIELD_REFERENCE);
                buffer.putByte(EventKind.GETFIELD_REFERENCE);
                if (eId != MISSING_FIELD_VAL)
                    buffer.putInt(eId);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
                if (ef.hasBaseObj()) {
                    int bId = getObjectId(b);
                    buffer.putInt(bId);
                }
                if (fId != MISSING_FIELD_VAL)
                    buffer.putInt(fId);
                if (ef.hasObj()) {
                    int oId = getObjectId(o);
                    buffer.putInt(oId);
                }
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void putfieldPrimitiveEvent(int eId, Object b, int fId) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.PUTFIELD_PRIMITIVE);
                buffer.putByte(EventKind.PUTFIELD_PRIMITIVE);
                if (eId != MISSING_FIELD_VAL)
                    buffer.putInt(eId);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
                if (ef.hasBaseObj()) {
                    int bId = getObjectId(b);
                    buffer.putInt(bId);
                }
                if (fId != MISSING_FIELD_VAL)
                    buffer.putInt(fId);
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void putfieldReferenceEvent(int eId, Object b, int fId, Object o) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.PUTFIELD_REFERENCE);
                buffer.putByte(EventKind.PUTFIELD_REFERENCE);
                if (eId != MISSING_FIELD_VAL)
                    buffer.putInt(eId);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
                if (ef.hasBaseObj()) {
                    int bId = getObjectId(b);
                    buffer.putInt(bId);
                }
                if (fId != MISSING_FIELD_VAL)
                    buffer.putInt(fId);
                if (ef.hasObj()) {
                    int oId = getObjectId(o);
                    buffer.putInt(oId);
                }
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void aloadPrimitiveEvent(int eId, Object b, int iId) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.ALOAD_PRIMITIVE);
                buffer.putByte(EventKind.ALOAD_PRIMITIVE);
                if (eId != MISSING_FIELD_VAL)
                    buffer.putInt(eId);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
                if (ef.hasBaseObj()) {
                    int bId = getObjectId(b);
                    buffer.putInt(bId);
                }
                if (ef.hasIdx())
                    buffer.putInt(iId);
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void aloadReferenceEvent(int eId, Object b, int iId, Object o) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.ALOAD_REFERENCE);
                buffer.putByte(EventKind.ALOAD_REFERENCE);
                if (eId != MISSING_FIELD_VAL)
                    buffer.putInt(eId);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
                if (ef.hasBaseObj()) {
                    int bId = getObjectId(b);
                    buffer.putInt(bId);
                }
                if (ef.hasIdx())
                    buffer.putInt(iId);
                if (ef.hasObj()) {
                    int oId = getObjectId(o);
                    buffer.putInt(oId);
                }
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void astorePrimitiveEvent(int eId, Object b, int iId) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.ASTORE_PRIMITIVE);
                buffer.putByte(EventKind.ASTORE_PRIMITIVE);
                if (eId != MISSING_FIELD_VAL)
                    buffer.putInt(eId);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
                if (ef.hasBaseObj()) {
                    int bId = getObjectId(b);
                    buffer.putInt(bId);
                }
                if (ef.hasIdx())
                    buffer.putInt(iId);
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void astoreReferenceEvent(int eId, Object b, int iId, Object o) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.ASTORE_REFERENCE);
                buffer.putByte(EventKind.ASTORE_REFERENCE);
                if (eId != MISSING_FIELD_VAL)
                    buffer.putInt(eId);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
                if (ef.hasBaseObj()) {
                    int bId = getObjectId(b);
                    buffer.putInt(bId);
                }
                if (ef.hasIdx())
                    buffer.putInt(iId);
                if (ef.hasObj()) {
                    int oId = getObjectId(o);
                    buffer.putInt(oId);
                }
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void returnPrimitiveEvent(int pId) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.RETURN_PRIMITIVE);
                buffer.putByte(EventKind.RETURN_PRIMITIVE);
                if (pId != MISSING_FIELD_VAL)
                    buffer.putInt(pId);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    
    }
    public synchronized static void returnReferenceEvent(int pId, Object o) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.RETURN_REFERENCE);
                buffer.putByte(EventKind.RETURN_REFERENCE);
                if (pId != MISSING_FIELD_VAL)
                    buffer.putInt(pId);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
                if (ef.hasObj()) {
                    int oId = getObjectId(o);
                    buffer.putInt(oId);
                }
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    
    }
    public synchronized static void explicitThrowEvent(int pId, Object o) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.EXPLICIT_THROW);
                buffer.putByte(EventKind.EXPLICIT_THROW);
                if (pId != MISSING_FIELD_VAL)
                    buffer.putInt(pId);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
                if (ef.hasObj()) {
                    int oId = getObjectId(o);
                    buffer.putInt(oId);
                }
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    
    }
    public synchronized static void implicitThrowEvent(Object o) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.IMPLICIT_THROW);
                buffer.putByte(EventKind.IMPLICIT_THROW);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
                if (ef.hasObj()) {
                    int oId = getObjectId(o);
                    buffer.putInt(oId);
                }
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void threadStartEvent(int iId, Object o) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.THREAD_START);
                buffer.putByte(EventKind.THREAD_START);
                if (iId != MISSING_FIELD_VAL)
                    buffer.putInt(iId);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
                if (ef.hasObj()) {
                    int oId = getObjectId(o);
                    buffer.putInt(oId);
                }
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void threadJoinEvent(int iId, Object o) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.THREAD_JOIN);
                buffer.putByte(EventKind.THREAD_JOIN);
                if (iId != MISSING_FIELD_VAL)
                    buffer.putInt(iId);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
                if (ef.hasObj()) {
                    int oId = getObjectId(o);
                    buffer.putInt(oId);
                }
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void acquireLockEvent(int lId, Object o) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.ACQUIRE_LOCK);
                buffer.putByte(EventKind.ACQUIRE_LOCK);
                if (lId != MISSING_FIELD_VAL) {
                    buffer.putInt(lId);
                }
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
                if (ef.hasObj()) {
                    int oId = getObjectId(o);
                    buffer.putInt(oId);
                }
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void releaseLockEvent(int rId, Object o) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.RELEASE_LOCK);
                buffer.putByte(EventKind.RELEASE_LOCK);
                if (rId != MISSING_FIELD_VAL) {
                    buffer.putInt(rId);
                }
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
                if (ef.hasObj()) {
                    int oId = getObjectId(o);
                    buffer.putInt(oId);
                }
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void waitEvent(int iId, Object o) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.WAIT);
                buffer.putByte(EventKind.WAIT);
                if (iId != MISSING_FIELD_VAL)
                    buffer.putInt(iId);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
                if (ef.hasObj()) {
                    int oId = getObjectId(o);
                    buffer.putInt(oId);
                }
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void notifyAnyEvent(int iId, Object o) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.NOTIFY_ANY);
                buffer.putByte(EventKind.NOTIFY_ANY);
                if (iId != MISSING_FIELD_VAL)
                    buffer.putInt(iId);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
                if (ef.hasObj()) {
                    int oId = getObjectId(o);
                    buffer.putInt(oId);
                }
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void notifyAllEvent(int iId, Object o) {
        if (trace) {
            trace = false;
            try {
                EventFormat ef = scheme.getEvent(InstrScheme.NOTIFY_ALL);
                buffer.putByte(EventKind.NOTIFY_ALL);
                if (iId != MISSING_FIELD_VAL)
                    buffer.putInt(iId);
                if (ef.hasThr()) {
                    int tId = getObjectId(Thread.currentThread());
                    buffer.putInt(tId);
                }
                if (ef.hasObj()) {
                    int oId = getObjectId(o);
                    buffer.putInt(oId);
                }
            } catch (IOException ex) { throw new RuntimeException(ex); }
            trace = true;
        }
    }
    public synchronized static void init(String args) {
        String[] a = args.split("=");
        String instrSchemeFileName = null;
        for (int i = 0; i < a.length; i += 2) {
            if (a[i].equals(InstrScheme.INSTR_SCHEME_FILE_KEY)) {
                instrSchemeFileName = a[i+1];
                break;
            }
        }
        if (instrSchemeFileName == null) {
            System.err.println("ERROR: EventHandler: Expected argument " +
                InstrScheme.INSTR_SCHEME_FILE_KEY + "=<FILE NAME>");
            System.exit(1);
        }
        scheme = InstrScheme.load(instrSchemeFileName);
        TraceEventHandler.init(args);
    }
}

