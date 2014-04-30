package chord.project;

/**
 * A Chord project comprising a set of tasks and a set of targets
 * produced/consumed by those tasks.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public abstract class Project {
    public static Project g() {
        return Config.classic ? ClassicProject.g() : ModernProject.g();
    }
    // build the project (process all java/dlog tasks)
    public abstract void build();
    // run specified tasks
    public abstract void run(String[] taskNames);
    // print specified relations
    public abstract void printRels(String[] relNames);
    // print the project (all tasks and trgts and dependencies b/w them)
    public abstract void print();
    protected void abort() {
        System.err.println("Found errors (see above). Exiting ...");
        System.exit(1);
    }
}
