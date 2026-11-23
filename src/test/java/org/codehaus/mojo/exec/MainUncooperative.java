package org.codehaus.mojo.exec;

/**
 * Created by IntelliJ IDEA. User: dsmiley Date: Jan 19, 2007 Time: 4:43:19 PM To change this template use File |
 * Settings | File Templates.
 */
public class MainUncooperative extends Thread {
    public static final String NAME = "uncooperative-daemon";

    /** A system property is shared across class loaders: a test sets it to let the lingering thread end silently. */
    public static final String QUIT_PROPERTY = "MainUncooperative.quit";

    // The plugin never stops a thread: it is interrupted once and keeps working
    public static final String INTERRUPTED_BUT_NOT_STOPPED = "1(interrupted)(f)2";

    public static void main(String... args) throws InterruptedException {
        Thread daemonThread = new MainUncooperative();
        daemonThread.setName(NAME);
        daemonThread.setDaemon(true);
        daemonThread.start();
        Thread.sleep(1000);
        // returns to caller now
    }

    final long SIMWORKTIME = 15 * 1000; // 15 seconds of work which is going to be more than exec:java wants to wait

    @Override
    public void run() {
        boolean interruptedOnce = false;
        long startedTime = System.currentTimeMillis();
        for (int lap = 1; true; lap++) {
            long remainingWork = SIMWORKTIME - (System.currentTimeMillis() - startedTime);
            if (remainingWork <= 0) {
                break; // done
            }
            try {
                System.out.print(lap);
                Thread.sleep(remainingWork); // simulates doing work
                System.out.print("(done)");
                break;
            } catch (InterruptedException e) {
                if (Boolean.getBoolean(QUIT_PROPERTY)) {
                    return;
                }
                // We want to ensure this only gets reported once. It's possible depending on a race condition for
                // ExecJavaMojo.terminateThreads() to interrupt this thread a second time.
                if (!interruptedOnce) {
                    System.out.print("(interrupted)");
                }
                interruptedOnce = true;

                // be uncooperative; don't quit and don't set interrupted status
            } finally {
                if (!Boolean.getBoolean(QUIT_PROPERTY)) {
                    System.out.print("(f)"); // printed on every lap
                }
            }
        }
    }
}
