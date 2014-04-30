package chord.instr;

/**
 * The kind of each event generated during an instrumented program's execution.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class EventKind {
    public static final byte ENTER_MAIN_METHOD = 0;
    public static final byte ENTER_METHOD = 1;
    public static final byte LEAVE_METHOD = 2;
    public static final byte BASIC_BLOCK = 3;
    public static final byte QUAD = 4;

    public static final byte BEF_METHOD_CALL = 5;
    public static final byte AFT_METHOD_CALL = 6;
    public static final byte BEF_NEW = 7;
    public static final byte AFT_NEW = 8;
    public static final byte NEWARRAY = 9;

    public static final byte GETSTATIC_PRIMITIVE = 10;
    public static final byte GETSTATIC_REFERENCE = 11;
    public static final byte PUTSTATIC_PRIMITIVE = 12;
    public static final byte PUTSTATIC_REFERENCE = 13;

    public static final byte GETFIELD_PRIMITIVE = 14;
    public static final byte GETFIELD_REFERENCE = 15;
    public static final byte PUTFIELD_PRIMITIVE = 16;
    public static final byte PUTFIELD_REFERENCE = 17;

    public static final byte ALOAD_PRIMITIVE = 18;
    public static final byte ALOAD_REFERENCE = 19;
    public static final byte ASTORE_PRIMITIVE = 20; 
    public static final byte ASTORE_REFERENCE = 21; 

    public static final byte RETURN_PRIMITIVE = 22;
    public static final byte RETURN_REFERENCE = 23;
    public static final byte EXPLICIT_THROW = 24;
    public static final byte IMPLICIT_THROW = 25;

    public static final byte THREAD_START = 26;
    public static final byte THREAD_JOIN = 27;
    public static final byte ACQUIRE_LOCK = 28;
    public static final byte RELEASE_LOCK = 29;
    public static final byte WAIT = 30;
    public static final byte NOTIFY_ANY = 31;
    public static final byte NOTIFY_ALL = 32;
}
