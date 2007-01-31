package org.codehaus.mojo.exec;

import java.util.Timer;
import java.util.TimerTask;

/**
 * @author <a href="mailto:dsmiley@mitre.org">David Smiley</a>
 */
public class MainWithThreads extends Thread
{
    public static final String ALL_EXITED = "t1(interrupted td)(cancelled timer)";
    public static final String TIMER_IGNORED = "t1(interrupted td)";

    /** 
     * both daemon threads will be interrupted as soon as the non daemon thread is done.
     * the responsive daemon thread will be interrupted right away.
     * - if the timer is cancelled (using 'cancelTimer' as argument), the timer will die on itself
     * after all the other threads
     * - if not, one must use a time out to stop joining on that unresponsive daemon thread
     **/
    public static void main( String[] args )
    {
        // long run so that we interrupt it before it ends itself
        Thread responsiveDaemonThread = new MainWithThreads( 60000, "td" );
        responsiveDaemonThread.setDaemon( true );
        responsiveDaemonThread.start();

        new MainWithThreads( 200, "t1" ).start();

        // Timer in Java <= 6 aren't interruptible
        final Timer t = new Timer( true );

        if ( optionsContains( args, "cancelTimer" ) )
        {
            t.schedule( new TimerTask()
            {
                public void run()
                {
                    System.out.print( "(cancelled timer)" );
                    t.cancel();
                }
            }, 400 );
        }
    }

    private static boolean optionsContains( String[] args, String option )
    {
        for (int i = 0; i < args.length; i++ )
        {
            if ( args[i].equals( option ) )
                return true;
        }
        return false;
    }

    private int millisecsToSleep;
    private String message;

    public MainWithThreads( int millisecsToSleep, String message )
    {
        this.millisecsToSleep = millisecsToSleep;
        this.message = message;
    }

    public void run()
    {
        try
        {
            Thread.sleep( millisecsToSleep );
        }
        catch ( InterruptedException e ) // IE's are a way to cancel a thread
        {
            System.out.print( "(interrupted " + message + ")" );
            return;
        }
        System.out.print( message );
    }
}
