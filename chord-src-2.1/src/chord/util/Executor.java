package chord.util;

import java.util.List;
import java.util.ArrayList;

/**
 * Utility for executing a set of given tasks serially or in parallel.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public final class Executor {
    // set of submitted tasks if this executor is parallel (and null if it is serial)
    private final List<Thread> tasks;
    // flag indicating that this executor is serial as opposed to parallel
    private final boolean serial;

    /**
     * Creates a new serial or parallel executor.
     *
     * @param serial Flag indicating that executor is serial as opposed to parallel.
     */
    public Executor(final boolean serial) {
        this.serial = serial;
        tasks = serial ? null : new ArrayList<Thread>();
    }

    /**
     * Executes given runnable task.
     *
     * If the executor is serial, this method returns after the submitted task is completed.
     * If the executor is parallel, this method starts a new thread for the submitted task and returns immediately.
     *
     * @param task A task to be executed.
     *
     * @throws IllegalArgumentException if {@code task} is {@code null}.
     */
    public synchronized void execute(final Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException();
        }
        if (serial)
            task.run();
        else {
            final Thread t = new Thread(task);
            tasks.add(t);
            t.start();
        }
    }

    /**
     * Waits for completion of all submitted tasks and returns afterwards.
     *
     * @throws InterruptedException if any occurs during waiting for tasks to be completed.
     */
    public synchronized void waitForCompletion() throws InterruptedException {
        if (!serial) {
            try {
                for (final Thread t : tasks) {
                    t.join();
                }
            } finally {
                tasks.clear();
            }
        }
    }

}
