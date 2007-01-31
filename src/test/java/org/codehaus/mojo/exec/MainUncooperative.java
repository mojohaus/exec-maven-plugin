package org.codehaus.mojo.exec;

/**
 * Created by IntelliJ IDEA.
 * User: dsmiley
 * Date: Jan 19, 2007
 * Time: 4:43:19 PM
 * To change this template use File | Settings | File Templates.
 */
public class MainUncooperative extends Thread
{
    public static final String SUCCESS = "1(interrupted)(f)2(f)";

    public static void main( String[] args )
        throws InterruptedException
    {
        Thread daemonThread = new MainUncooperative();
        daemonThread.setDaemon( true );
        daemonThread.start();
        Thread.sleep( 1000 );
        //returns to caller now
    }

    final long SIMWORKTIME = 15*1000;//15 seconds of work which is going to be more than exec:java wants to wait

    public void run()
    {
        boolean interruptedOnce = false;
        long startedTime = System.currentTimeMillis();
        for(int lap = 1; true; lap++ )
        {
            long remainingWork = SIMWORKTIME - (System.currentTimeMillis() - startedTime);
            if ( remainingWork <= 0 )
            {
                break;//done
            }
            try
            {
                System.out.print( lap );
                Thread.sleep(remainingWork);//simulates doing work
                System.out.print( "(done)" );
                break;
            }
            catch ( InterruptedException e )
            {
                //We want to ensure this only gets reported once. It's possible depending on a race condition for
                // ExecJavaMojo.terminateThreads() to interrupt this thread a second time.
                if ( ! interruptedOnce )
                {
                    System.out.print( "(interrupted)" );
                }
                interruptedOnce = true;

                //be uncooperative; don't quit and don't set interrupted status
            }
            finally
            {
                System.out.print("(f)");//we should see this if Thread.stop() is called
            }
        }
    }
}
