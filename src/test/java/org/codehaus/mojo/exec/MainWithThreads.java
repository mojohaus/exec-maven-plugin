package org.codehaus.mojo.exec;

/**
 * @author <a href="mailto:dsmiley@mitre.org">David Smiley</a>
 */
public class MainWithThreads extends Thread
{
    public static final String SUCCESS = "ABCD";

    public static void main( String[] args )
    {
        Thread daemonThread = new MainWithThreads(10,"Daemon done" );
        daemonThread.setDaemon( true );
        daemonThread.start();
        Thread regThread = new MainWithThreads(2,"AB");//not daemon by default
        regThread.start();
        regThread = new MainWithThreads(4,"CD");//not daemon by default
        regThread.start();
        //returns to caller now
    }

    int secsToSleep;
    String message;

    public MainWithThreads( int secsToSleep, String message )
    {
        this.secsToSleep = secsToSleep;
        this.message = message;
    }

    public void run()
    {
        try
        {
            Thread.sleep( secsToSleep*1000 );
        }
        catch ( InterruptedException e ) // IE's are a way to cancel a thread
        {
            return;
        }
        System.out.print(message);
    }
}
