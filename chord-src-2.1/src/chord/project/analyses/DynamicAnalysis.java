package chord.project.analyses;

import gnu.trove.set.hash.TIntHashSet;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.Stack;
import java.util.Collections;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import chord.analyses.basicblock.DomB;
import chord.analyses.method.DomM;
import chord.instr.EventKind;
import chord.instr.InstrScheme;
import chord.instr.Instrumentor;
import chord.instr.TraceTransformer;
import chord.instr.TracePrinter;
import chord.instr.InstrScheme.EventFormat;
import chord.program.CFGLoopFinder;
import chord.project.ClassicProject;
import chord.project.Messages;
import chord.project.Config;
import chord.instr.BasicInstrumentor;
import chord.runtime.EventHandler;
import chord.util.ByteBufferedFile;
import chord.util.Utils;
import chord.util.tuple.object.Pair;

/**
 * Generic implementation of a dynamic analysis that allows choosing
 * from a limited but commonly-used set of events, such as
 * method entry/exit, field reads/writes, lock acquires/releases, etc.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 * @author omertripp (omertrip@post.tau.ac.il)
 */
public class DynamicAnalysis extends BasicDynamicAnalysis {
    ///// Shorthands for error/warning messages in this class

    private static final String EVENT_NOT_HANDLED =
        "ERROR: DynamicAnalysis: Analysis named '%s' must either override method '%s' or omit the corresponding event from its instrumentation scheme.";
    private static final String NO_INSTR_SCHEME =
        "ERROR: Dynamicanalysis: Analysis named '%s' must override method 'InstrScheme getInstrScheme()'.";
    private static final String INVALID_TRACE_KIND =
        "ERROR: DynamicAnalysis: Analysis named '%s' must use (regular or piped) trace file.";

    ///// Data structures for loop entry/iter/leave events

    private static abstract class Record {
    }
    private static class LoopRecord extends Record {
        public final int b;
        public LoopRecord(int b) {
            this.b = b;
        }
    }
    private static class MethRecord extends Record {
        public final int m;
        public MethRecord(int m) {
            this.m = m;
        }
    }

    /**
     * The instrumentation scheme for this dynamic analysis.
     */
    protected InstrScheme scheme;
    private DomM domM;
    private DomB domB;
    private boolean isUserReqEnterMethodEvent;
    private boolean isUserReqLeaveMethodEvent;
    private boolean isUserReqBasicBlockEvent;
    private boolean hasEnterAndLeaveLoopEvent;
    private final TIntObjectHashMap<Stack<Record>> stacks =
        new TIntObjectHashMap<Stack<Record>>(1);
    private final TIntObjectHashMap<TIntHashSet> loopHead2body =
        new TIntObjectHashMap<TIntHashSet>(16);
    private TIntHashSet visited4loops = new TIntHashSet();

    // subclasses MUST override unless this dynamic analysis
    // is performed using an instrumentation scheme (and traces)
    // stored on disk from a previous run of Chord
    public InstrScheme getInstrScheme() {
        Messages.fatal(NO_INSTR_SCHEME, getName());
        return null;
    }

    public String getInstrSchemeFileName() {
        return Config.instrSchemeFileName;
    }

    @Override
    public Class getInstrumentorClass() {
        return Instrumentor.class;
    }

    @Override
    public Class getEventHandlerClass() {
        return EventHandler.class;
    }

    @Override
    public Map<String, String> getInstrumentorArgs() {
        Map<String, String> args = super.getInstrumentorArgs();
        args.put(InstrScheme.INSTR_SCHEME_FILE_KEY, getInstrSchemeFileName());
        args.put(BasicInstrumentor.EVENT_HANDLER_CLASS_KEY, getEventHandlerClass().getName());
        return args;
    }

    @Override
    public Map<String, String> getEventHandlerArgs() {
        Map<String, String> args = super.getEventHandlerArgs();
        args.put(InstrScheme.INSTR_SCHEME_FILE_KEY, getInstrSchemeFileName());
        return args;
    }

    @Override
    public List<Runnable> getTraceTransformers() {
        if (!scheme.needsTraceTransform())
            return Collections.EMPTY_LIST;
        Runnable r = new Runnable() {
            public void run() {
                String src = getTraceFileName(1);
                String dst = getTraceFileName(0);
                // System.out.println("TRACE 1");
                // (new TracePrinter(src, scheme)).run();
                (new TraceTransformer(src, dst, scheme)).run();
                // System.out.println("TRACE 2");
                // (new TracePrinter(dst, scheme)).run();
            }
        };
        List<Runnable> l = new ArrayList<Runnable>(1);
        l.add(r);
        return l;
    }

    @Override
    public boolean canReuseTraces() {
        return Utils.exists(getInstrSchemeFileName()) && super.canReuseTraces();
    }

    @Override
    public void run() {
        String instrSchemeFileName = getInstrSchemeFileName();
        boolean reuseTraces = canReuseTraces();
        if (reuseTraces) {
            scheme = InstrScheme.load(instrSchemeFileName);
        } else
            scheme = getInstrScheme();
        isUserReqEnterMethodEvent = scheme.getEvent(InstrScheme.ENTER_METHOD).present(); 
        isUserReqLeaveMethodEvent = scheme.getEvent(InstrScheme.LEAVE_METHOD).present(); 
        isUserReqBasicBlockEvent = scheme.hasBasicBlockEvent(); 
        hasEnterAndLeaveLoopEvent = scheme.hasEnterAndLeaveLoopEvent();
        if (scheme.hasEnterAndLeaveLoopEvent()) {
            // below events are mandatory for consistent handling of
            // enter/iter/leave loop events
            if (reuseTraces) {
                // TODO: assert that scheme has below events
            } else {
                scheme.setEnterMethodEvent(true, true);
                scheme.setLeaveMethodEvent(true, true);
                scheme.setBasicBlockEvent();
            }
            init4loopConsistency();
        }
        scheme.save(instrSchemeFileName);
        super.run();
    }

    private void init4loopConsistency() {
        domM = (DomM) ClassicProject.g().getTrgt("M");
        ClassicProject.g().runTask(domM);
        domB = (DomB) ClassicProject.g().getTrgt("B");
        ClassicProject.g().runTask(domB);
    }
    
    private void onLoopStart(int b, int t) {
        Stack<Record> stack = stacks.get(t);
        assert (stack != null);
        stack.add(new LoopRecord(b));
        processEnterLoop(b, t);
    }

    private void processBasicBlock4loopConsistency(int b, int t) {
        Stack<Record> stack = stacks.get(t);
        assert (stack != null);
        // Remove dead loop records from the stack.
        boolean hasRemoved;
        do {
            hasRemoved = false;
            Record r = stack.peek();
            if (r instanceof LoopRecord) {
                LoopRecord lr = (LoopRecord) r;
                TIntHashSet loopBody = loopHead2body.get(lr.b);
                assert (loopBody != null);
                if (!loopBody.contains(b)) {
                    stack.pop();
                    processLeaveLoop(lr.b, t);
                    hasRemoved = true;
                }
            }
        } while (hasRemoved);
        boolean isLoopHead = loopHead2body.containsKey(b);
        if (isLoopHead) {
            Record r = stack.peek();
            if (r instanceof MethRecord) {
                onLoopStart(b, t);
            } else {
                assert (r instanceof LoopRecord);
                LoopRecord lr = (LoopRecord) r;
                if (lr.b == b) {
                    processLoopIteration(lr.b, t);
                } else {
                    onLoopStart(b, t);
                }
            }
        }
    }
    
    private void processLeaveMethod4loopConsistency(int m, int t) {
        Stack<Record> stack = stacks.get(t);
        assert (stack != null);
        if (!stack.isEmpty()) {
            while (stack.peek() instanceof LoopRecord) {
                LoopRecord top = (LoopRecord) stack.pop();
                processLeaveLoop(top.b, t);
            }
            
            // The present method should be at the stop of the stack.
            Record top = stack.peek();
            assert (top instanceof MethRecord);
            MethRecord mr = (MethRecord) top;
            if (mr.m == m) {
                stack.pop();
            }
        }
    }

    private void processEnterMethod4loopConsistency(int m, int t) {
        Stack<Record> stack = stacks.get(t);
        if (stack == null) {
            stack = new Stack<Record>();
            stacks.put(t, stack);
        }
        stack.add(new MethRecord(m));
        if (!visited4loops.contains(m)) {
            visited4loops.add(m);
            jq_Method mthd = domM.get(m);
            // Perform a slightly eager computation to map each loop header
            // to its body (in terms of <code>DomB</code>).
            ControlFlowGraph cfg = mthd.getCFG();
            CFGLoopFinder finder = new CFGLoopFinder();
            finder.visit(cfg);
            for (BasicBlock head : finder.getLoopHeads()) {
                TIntHashSet S = new TIntHashSet();
                int bh = domB.indexOf(head);
                assert (bh != -1);
                loopHead2body.put(bh, S);
                Set<BasicBlock> loopBody = finder.getLoopBody(head);
                for (BasicBlock bb : loopBody) {
                    int b2 = domB.indexOf(bb);
                    assert (b2 != -1);
                    S.add(b2);
                }
            }
        }
    }
    
    @Override
    public void handleEvent(ByteBufferedFile buffer) throws IOException {
        byte opcode = buffer.getByte();
        switch (opcode) {
        case EventKind.ENTER_MAIN_METHOD:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.ENTER_MAIN_METHOD);
            int t = ef.hasThr() ? buffer.getInt() : -1;
            processEnterMainMethod(t);
            break;
        }
        case EventKind.ENTER_METHOD:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.ENTER_METHOD);
            int m = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            if (hasEnterAndLeaveLoopEvent) {
                processEnterMethod4loopConsistency(m, t);
            }
            if (isUserReqEnterMethodEvent) {
                processEnterMethod(m, t);
            }
            break;
        }
        case EventKind.LEAVE_METHOD:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.LEAVE_METHOD);
            int m = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            if (hasEnterAndLeaveLoopEvent) {
                processLeaveMethod4loopConsistency(m, t);
            }
            if (isUserReqLeaveMethodEvent) {
                processLeaveMethod(m, t);
            }
            break;
        }
        case EventKind.BASIC_BLOCK:
        {
            int b = buffer.getInt();
            int t = buffer.getInt();
            if (hasEnterAndLeaveLoopEvent) {
                processBasicBlock4loopConsistency(b, t);
            }
            if (isUserReqBasicBlockEvent) {
                processBasicBlock(b, t);
            }
            break;
        }
        case EventKind.QUAD:
        {
            int q = buffer.getInt();
            int t = buffer.getInt();
            processQuad(q, t);
            break;
        }
        case EventKind.BEF_METHOD_CALL:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.BEF_METHOD_CALL);
            int i = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            int o = ef.hasObj() ? buffer.getInt() : -1;
            processBefMethodCall(i, t, o);
            break;
        }
        case EventKind.AFT_METHOD_CALL:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.AFT_METHOD_CALL);
            int i = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            int o = ef.hasObj() ? buffer.getInt() : -1;
            processAftMethodCall(i, t, o);
            break;
        }
        case EventKind.BEF_NEW:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.BEF_NEW);
            int h = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            int o = ef.hasObj() ? buffer.getInt() : -1;
            processBefNew(h, t, o);
            break;
        }
        case EventKind.AFT_NEW:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.AFT_NEW);
            int h = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            int o = ef.hasObj() ? buffer.getInt() : -1;
            processAftNew(h, t, o);
            break;
        }
        case EventKind.NEWARRAY:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.NEWARRAY);
            int h = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            int o = ef.hasObj() ? buffer.getInt() : -1;
            processNewArray(h, t, o);
            break;
        }
        case EventKind.GETSTATIC_PRIMITIVE:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.GETSTATIC_PRIMITIVE);
            int e = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            int b = ef.hasBaseObj() ? buffer.getInt() : -1;
            int f = ef.hasFld() ? buffer.getInt() : -1;
            processGetstaticPrimitive(e, t, b, f);
            break;
        }
        case EventKind.GETSTATIC_REFERENCE:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.GETSTATIC_REFERENCE);
            int e = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            int b = ef.hasBaseObj() ? buffer.getInt() : -1;
            int f = ef.hasFld() ? buffer.getInt() : -1;
            int o = ef.hasObj() ? buffer.getInt() : -1;
            processGetstaticReference(e, t, b, f, o);
            break;
        }
        case EventKind.PUTSTATIC_PRIMITIVE:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.PUTSTATIC_PRIMITIVE);
            int e = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            int b = ef.hasBaseObj() ? buffer.getInt() : -1;
            int f = ef.hasFld() ? buffer.getInt() : -1;
            processPutstaticPrimitive(e, t, b, f);
            break;
        }
        case EventKind.PUTSTATIC_REFERENCE:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.PUTSTATIC_REFERENCE);
            int e = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            int b = ef.hasBaseObj() ? buffer.getInt() : -1;
            int f = ef.hasFld() ? buffer.getInt() : -1;
            int o = ef.hasObj() ? buffer.getInt() : -1;
            processPutstaticReference(e, t, b, f, o);
            break;
        }
        case EventKind.GETFIELD_PRIMITIVE:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.GETFIELD_PRIMITIVE);
            int e = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            int b = ef.hasBaseObj() ? buffer.getInt() : -1;
            int f = ef.hasFld() ? buffer.getInt() : -1;
            processGetfieldPrimitive(e, t, b, f);
            break;
        }
        case EventKind.GETFIELD_REFERENCE:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.GETFIELD_REFERENCE);
            int e = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            int b = ef.hasBaseObj() ? buffer.getInt() : -1;
            int f = ef.hasFld() ? buffer.getInt() : -1;
            int o = ef.hasObj() ? buffer.getInt() : -1;
            processGetfieldReference(e, t, b, f, o);
            break;
        }
        case EventKind.PUTFIELD_PRIMITIVE:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.PUTFIELD_PRIMITIVE);
            int e = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            int b = ef.hasBaseObj() ? buffer.getInt() : -1;
            int f = ef.hasFld() ? buffer.getInt() : -1;
            processPutfieldPrimitive(e, t, b, f);
            break;
        }
        case EventKind.PUTFIELD_REFERENCE:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.PUTFIELD_REFERENCE);
            int e = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            int b = ef.hasBaseObj() ? buffer.getInt() : -1;
            int f = ef.hasFld() ? buffer.getInt() : -1;
            int o = ef.hasObj() ? buffer.getInt() : -1;
            processPutfieldReference(e, t, b, f, o);
            break;
        }
        case EventKind.ALOAD_PRIMITIVE:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.ALOAD_PRIMITIVE);
            int e = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            int b = ef.hasBaseObj() ? buffer.getInt() : -1;
            int i = ef.hasIdx() ? buffer.getInt() : -1;
            processAloadPrimitive(e, t, b, i);
            break;
        }
        case EventKind.ALOAD_REFERENCE:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.ALOAD_REFERENCE);
            int e = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            int b = ef.hasBaseObj() ? buffer.getInt() : -1;
            int i = ef.hasIdx() ? buffer.getInt() : -1;
            int o = ef.hasObj() ? buffer.getInt() : -1;
            processAloadReference(e, t, b, i, o);
            break;
        }
        case EventKind.ASTORE_PRIMITIVE:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.ASTORE_PRIMITIVE);
            int e = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            int b = ef.hasBaseObj() ? buffer.getInt() : -1;
            int i = ef.hasIdx() ? buffer.getInt() : -1;
            processAstorePrimitive(e, t, b, i);
            break;
        }
        case EventKind.ASTORE_REFERENCE:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.ASTORE_REFERENCE);
            int e = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            int b = ef.hasBaseObj() ? buffer.getInt() : -1;
            int i = ef.hasIdx() ? buffer.getInt() : -1;
            int o = ef.hasObj() ? buffer.getInt() : -1;
            processAstoreReference(e, t, b, i, o);
            break;
        }
        case EventKind.RETURN_PRIMITIVE:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.RETURN_PRIMITIVE);
            int p = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            processReturnPrimitive(p, t);
            break;
        }
        case EventKind.RETURN_REFERENCE:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.RETURN_REFERENCE);
            int p = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            int o = ef.hasObj() ? buffer.getInt() : -1;
            processReturnReference(p, t, o);
            break;
        }
        case EventKind.EXPLICIT_THROW:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.EXPLICIT_THROW);
            int p = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            int o = ef.hasObj() ? buffer.getInt() : -1;
            processExplicitThrow(p, t, o);
            break;
        }
        case EventKind.IMPLICIT_THROW:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.IMPLICIT_THROW);
            int p = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            int o = ef.hasObj() ? buffer.getInt() : -1;
            processImplicitThrow(p, t, o);
            break;
        }
        case EventKind.THREAD_START:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.THREAD_START);
            int p = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            int o = ef.hasObj() ? buffer.getInt() : -1;
            processThreadStart(p, t, o);
            break;
        }
        case EventKind.THREAD_JOIN:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.THREAD_JOIN);
            int p = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            int o = ef.hasObj() ? buffer.getInt() : -1;
            processThreadJoin(p, t, o);
            break;
        }
        case EventKind.ACQUIRE_LOCK:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.ACQUIRE_LOCK);
            int p = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            int l = ef.hasObj() ? buffer.getInt() : -1;
            processAcquireLock(p, t, l);
            break;
        }
        case EventKind.RELEASE_LOCK:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.RELEASE_LOCK);
            int p = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            int l = ef.hasObj() ? buffer.getInt() : -1;
            processReleaseLock(p, t, l);
            break;
        }
        case EventKind.WAIT:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.WAIT);
            int p = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            int l = ef.hasObj() ? buffer.getInt() : -1;
            processWait(p, t, l);
            break;
        }
        case EventKind.NOTIFY_ANY:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.NOTIFY_ANY);
            int p = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            int l = ef.hasObj() ? buffer.getInt() : -1;
            processNotifyAny(p, t, l);
            break;
        }
        case EventKind.NOTIFY_ALL:
        {
            EventFormat ef = scheme.getEvent(InstrScheme.NOTIFY_ALL);
            int p = ef.hasLoc() ? buffer.getInt() : -1;
            int t = ef.hasThr() ? buffer.getInt() : -1;
            int l = ef.hasObj() ? buffer.getInt() : -1;
            processNotifyAll(p, t, l);
            break;
        }
        default:
            throw new RuntimeException("Unknown opcode: " + opcode);
        }
    }
    
    public void processEnterMainMethod(int t) { }
    public void processEnterMethod(int m, int t) { }
    public void processLeaveMethod(int m, int t) { }
    public void processEnterLoop(int w, int t) { }
    public void processLoopIteration(int w, int t) { }
    public void processLeaveLoop(int w, int t) { }
    public void processBasicBlock(int b, int t) { }
    public void processQuad(int p, int t) { }
    public void processBefMethodCall(int i, int t, int o) { }
    public void processAftMethodCall(int i, int t, int o) { }
    public void processBefNew(int h, int t, int o) { }
    public void processAftNew(int h, int t, int o) { }
    public void processNewArray(int h, int t, int o) { }
    public void processGetstaticPrimitive(int e, int t, int b, int f) { }
    public void processGetstaticReference(int e, int t, int b, int f, int o) { }
    public void processPutstaticPrimitive(int e, int t, int b, int f) { }
    public void processPutstaticReference(int e, int t, int b, int f, int o) { }
    public void processGetfieldPrimitive(int e, int t, int b, int f) { }
    public void processGetfieldReference(int e, int t, int b, int f, int o) { }
    public void processPutfieldPrimitive(int e, int t, int b, int f) { }
    public void processPutfieldReference(int e, int t, int b, int f, int o) { }
    public void processAloadPrimitive(int e, int t, int b, int i) { }
    public void processAloadReference(int e, int t, int b, int i, int o) { }
    public void processAstorePrimitive(int e, int t, int b, int i) { }
    public void processAstoreReference(int e, int t, int b, int i, int o) { }
    public void processReturnPrimitive(int p, int t) { }
    public void processReturnReference(int p, int t, int o) { }
    public void processExplicitThrow(int p, int t, int o) { }
    public void processImplicitThrow(int p, int t, int o) { }
    public void processThreadStart(int i, int t, int o) { }
    public void processThreadJoin(int i, int t, int o) { }
    public void processAcquireLock(int l, int t, int o) { }
    public void processReleaseLock(int r, int t, int o) { }
    public void processWait(int i, int t, int o) { }
    public void processNotifyAny(int i, int t, int o) { }
    public void processNotifyAll(int i, int t, int o) { }
}
