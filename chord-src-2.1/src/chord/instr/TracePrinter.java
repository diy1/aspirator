package chord.instr;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.FileWriter;

import joeq.Compiler.Quad.Quad;
import chord.project.Config;
import chord.analyses.alloc.DomH;
import chord.analyses.invk.DomI;
import chord.project.ClassicProject;
import chord.instr.InstrScheme.EventFormat;
import chord.util.ByteBufferedFile;
import chord.runtime.EventHandler;

/**
 * Functionality for pretty-printing a trace of events generated during an instrumented program's execution.
 * <p>
 * Recognized system properties:
 * <ul>
 * <li><tt>chord.trace.block.size</tt> (default is 4096): the number of bytes to be read from the original
 * trace file or written to the transformed trace file in a single file operation.</li>
 * </ul>
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class TracePrinter {
    private final String traceFileName;
    private final InstrScheme scheme;
    /**
     * Initializes a trace printer.
     * 
     * @param traceFileName Location of the file containing the trace to be printed.
     * @param scheme        Instrumentation scheme used to generate this trace.
     */
    public TracePrinter(String traceFileName, InstrScheme scheme) {
        this.traceFileName = traceFileName;
        this.scheme = scheme;
    }
    /**
     * Runs the trace printer.
     */
    public void run() {
        try {
            DomH domH = (DomH) ClassicProject.g().getTrgt("H");
            ClassicProject.g().runTask(domH);
            DomI domI = (DomI) ClassicProject.g().getTrgt("I");
            ClassicProject.g().runTask(domI);
            int traceBlockSize = Config.traceBlockSize;
            ByteBufferedFile buffer = new ByteBufferedFile(traceBlockSize, traceFileName, true);
            while (!buffer.isDone()) {
                byte opcode = buffer.getByte();
                switch (opcode) {
                case EventKind.ENTER_MAIN_METHOD:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.ENTER_MAIN_METHOD);
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    System.out.println("ENTER_MAIN_METHOD " + t);
                    break;
                }
                case EventKind.ENTER_METHOD:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.ENTER_METHOD);
                    int m = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    System.out.println("ENTER_METHOD " + m + " " + t);
                    break;
                }
                case EventKind.LEAVE_METHOD:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.LEAVE_METHOD);
                    int m = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    System.out.println("LEAVE_METHOD " + m + " " + t);
                    break;
                }
                case EventKind.BASIC_BLOCK:
                {
                    int b = buffer.getInt();
                    int t = buffer.getInt();
                    System.out.println("BASIC_BLOCK " + b + " " + t);
                    break;
                }
                case EventKind.QUAD:
                {
                    int p = buffer.getInt();
                    int t = buffer.getInt();
                    System.out.println("QUAD " + p + " " + t);
                    break;
                }
                case EventKind.BEF_METHOD_CALL:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.BEF_METHOD_CALL);
                    int i = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int o = ef.hasObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    String iStr = i > 0 ? domI.get(i).toJavaLocStr() : Integer.toString(i);
                    System.out.println("BEF_METHOD_CALL " + iStr + " " + t + " " + o);
                    break;
                }
                case EventKind.AFT_METHOD_CALL:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.AFT_METHOD_CALL);
                    int i = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int o = ef.hasObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    String iStr = i > 0 ? domI.get(i).toJavaLocStr() : Integer.toString(i);
                    System.out.println("AFT_METHOD_CALL " + iStr + " " + t + " " + o);
                    break;
                }
                case EventKind.BEF_NEW:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.BEF_NEW);
                    int h = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int o = ef.hasObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    String hStr = h > 0 ? ((Quad) domH.get(h)).toJavaLocStr() : Integer.toString(h);
                    System.out.println("BEF_NEW " + hStr + " " + t + " " + o);
                    break;
                }
                case EventKind.AFT_NEW:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.AFT_NEW);
                    int h = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int o = ef.hasObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    String hStr = h > 0 ? ((Quad) domH.get(h)).toJavaLocStr() : Integer.toString(h);
                    System.out.println("AFT_NEW " + hStr + " " + t + " " + o);
                    break;
                }
                case EventKind.NEWARRAY:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.NEWARRAY);
                    int h = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int o = ef.hasObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    System.out.println("NEWARRAY " + h + " " + t + " " + o);
                    break;
                }
                case EventKind.GETSTATIC_PRIMITIVE:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.GETSTATIC_PRIMITIVE);
                    int e = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int b = ef.hasBaseObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int f = ef.hasFld() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    System.out.println("GETSTATIC_PRIMITIVE " + e + " " + t + " " + b + " " + f);
                    break;
                }
                case EventKind.GETSTATIC_REFERENCE:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.GETSTATIC_REFERENCE);
                    int e = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int b = ef.hasBaseObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int f = ef.hasFld() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int o = ef.hasObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    System.out.println("GETSTATIC_REFERENCE " + e + " " + t + " " + b + " " + f + " " + o);
                    break;
                }
                case EventKind.PUTSTATIC_PRIMITIVE:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.PUTSTATIC_PRIMITIVE);
                    int e = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int b = ef.hasBaseObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int f = ef.hasFld() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    System.out.println("PUTSTATIC_PRIMITIVE " + e + " " + t + " " + b + " " + f);
                    break;
                }
                case EventKind.PUTSTATIC_REFERENCE:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.PUTSTATIC_REFERENCE);
                    int e = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int b = ef.hasBaseObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int f = ef.hasFld() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int o = ef.hasObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    System.out.println("PUTSTATIC_REFERENCE " + e + " " + t + " " + b + " " + f + " " + o);
                    break;
                }
                case EventKind.GETFIELD_PRIMITIVE:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.GETFIELD_PRIMITIVE);
                    int e = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int b = ef.hasBaseObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int f = ef.hasFld() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    System.out.println("GETFIELD_PRIMITIVE " + e + " " + t + " " + b + " " + f);
                    break;
                }
                case EventKind.GETFIELD_REFERENCE:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.GETFIELD_REFERENCE);
                    int e = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int b = ef.hasBaseObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int f = ef.hasFld() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int o = ef.hasObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    System.out.println("GETFIELD_REFERENCE " + e + " " + t + " " + b + " " + f + " " + o);
                    break;
                }
                case EventKind.PUTFIELD_PRIMITIVE:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.PUTFIELD_PRIMITIVE);
                    int e = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int b = ef.hasBaseObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int f = ef.hasFld() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    System.out.println("PUTFIELD_PRIMITIVE " + e + " " + t + " " + b + " " + f);
                    break;
                }
                case EventKind.PUTFIELD_REFERENCE:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.PUTFIELD_REFERENCE);
                    int e = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int b = ef.hasBaseObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int f = ef.hasFld() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int o = ef.hasObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    System.out.println("PUTFIELD_REFERENCE " + e + " " + t + " " + b + " " + f + " " + o);
                    break;
                }
                case EventKind.ALOAD_PRIMITIVE:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.ALOAD_PRIMITIVE);
                    int e = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int b = ef.hasBaseObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int i = ef.hasIdx() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    System.out.println("ALOAD_PRIMITIVE " + e + " " + t + " " + b + " " + i);
                    break;
                }
                case EventKind.ALOAD_REFERENCE:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.ALOAD_REFERENCE);
                    int e = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int b = ef.hasBaseObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int i = ef.hasIdx() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int o = ef.hasObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    System.out.println("ALOAD_REFERENCE " + e + " " + t + " " + b + " " + i + " " + o);
                    break;
                }
                case EventKind.ASTORE_PRIMITIVE:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.ASTORE_PRIMITIVE);
                    int e = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int b = ef.hasBaseObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int i = ef.hasIdx() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    System.out.println("ASTORE_PRIMITIVE " + e + " " + t + " " + b + " " + i);
                    break;
                }
                case EventKind.ASTORE_REFERENCE:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.ASTORE_REFERENCE);
                    int e = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int b = ef.hasBaseObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int i = ef.hasIdx() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int o = ef.hasObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    System.out.println("ASTORE_REFERENCE " + e + " " + t + " " + b + " " + i + " " + o);
                    break;
                }
                case EventKind.THREAD_START:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.THREAD_START);
                    int i = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int o = ef.hasObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    System.out.println("THREAD_START " + i + " " + t + " " + o);
                    break;
                }
                case EventKind.THREAD_JOIN:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.THREAD_JOIN);
                    int i = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int o = ef.hasObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    System.out.println("THREAD_JOIN " + i + " " + t + " " + o);
                    break;
                }
                case EventKind.ACQUIRE_LOCK:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.ACQUIRE_LOCK);
                    int l = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int o = ef.hasObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    System.out.println("ACQUIRE_LOCK " + l + " " + t + " " + o);
                    break;
                }
                case EventKind.RELEASE_LOCK:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.RELEASE_LOCK);
                    int r = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int o = ef.hasObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    System.out.println("RELEASE_LOCK " + r + " " + t + " " + o);
                    break;
                }
                case EventKind.WAIT:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.WAIT);
                    int i = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int o = ef.hasObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    System.out.println("WAIT " + i + " " + t + " " + o);
                    break;
                }
                case EventKind.NOTIFY_ANY:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.NOTIFY_ANY);
                    int i = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int o = ef.hasObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    System.out.println("NOTIFY_ANY " + i + " " + t + " " + o);
                    break;
                }
                case EventKind.NOTIFY_ALL:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.NOTIFY_ALL);
                    int i = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int o = ef.hasObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    System.out.println("NOTIFY_ALL " + i + " " + t + " " + o);
                    break;
                }
                case EventKind.RETURN_PRIMITIVE:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.RETURN_PRIMITIVE);
                    int p = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    System.out.println("RETURN_PRIMITIVE " + p + " " + t);
                    break;
                }
                case EventKind.RETURN_REFERENCE:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.RETURN_REFERENCE);
                    int p = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int o = ef.hasObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    System.out.println("RETURN_REFERENCE " + p + " " + t + " " + o);
                    break;
                }
                case EventKind.EXPLICIT_THROW:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.EXPLICIT_THROW);
                    int p = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int o = ef.hasObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    System.out.println("EXPLICIT_THROW " + p + " " + t + " " + o);
                    break;
                }
                case EventKind.IMPLICIT_THROW:
                {
                    EventFormat ef = scheme.getEvent(InstrScheme.IMPLICIT_THROW);
                    int p = ef.hasLoc() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int t = ef.hasThr() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    int o = ef.hasObj() ? buffer.getInt() : EventHandler.MISSING_FIELD_VAL;
                    System.out.println("IMPLICIT_THROW " + p + " " + t + " " + o);
                    break;
                }
                default: 
                    throw new RuntimeException("Unknown opcode: " + opcode);
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}

