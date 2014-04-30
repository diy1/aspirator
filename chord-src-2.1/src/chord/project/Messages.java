package chord.project;

/**
 * Utility for logging messages during Chord's execution.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class Messages {
    private Messages() { }
    public static void log(String format, Object... args) {
        String msg = String.format(format, args);
        System.out.println(msg);
    }
    public static void warn(String format, Object... args) {
        String msg = String.format(format, args);
        System.err.println(msg);
    }
    public static void error(String format, Object... args) {
        String msg = String.format(format, args);
        System.err.println(msg);
    }
    public static void fatal(String format, Object... args) {
        String msg = String.format(format, args);
        Error ex = new Error(msg);
        ex.printStackTrace();
        System.exit(1);
    }
    public static void fatal(Throwable ex) {
        ex.printStackTrace();
        System.exit(1);
    }
}

