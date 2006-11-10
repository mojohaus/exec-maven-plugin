package org.codehaus.mojo.exec.project5libB;

import org.apache.log4j.Logger;

/**
 * Used for manual integrationtest of the java goal.
 *
 */
public class App 
{
    static Logger log = Logger.getLogger(App.class);
    public static void main( String[] args )
    {
        System.out.println("I was started. So obviously I found the main class");
        
        App app = new App();

    try
        {
            Class fileUtils = app.getClass().getClassLoader().loadClass( "org.codehaus.mojo.exec.project5lib.ExampleLibrary" );
            if ( null != fileUtils )
            {
                System.out.println( "Found the ExampleLibrary" );
            }
        }

        catch ( Exception e )
        {
            System.out.println( "Did not find the ExampleLibrary" );
        }
        
        try
        {
            Class log = app.getClass().getClassLoader().loadClass( "org.apache.log4j.Logger" );
            if ( null != log )
            {
                System.out.println( "Found the compile dependency" );
            }
        }
        catch ( Exception e )
        {
            System.out.println( "Did not find the compile dependency" );
        }
        
        try
        {
            Class testCase = app.getClass().getClassLoader().loadClass( "junit.framework.TestCase" );
            if ( null != testCase )
            {
                System.out.println( "Found the test dependency" );
            }
        }
        catch ( Exception e )
        {
            System.out.println( "Did not found the test dependency" );
        }
        
        try
        {
            Class fileUtils = app.getClass().getClassLoader().loadClass( "org.apache.commons.io.FileUtils" );
            if ( null != fileUtils )
            {
                System.out.println( "Found the runtime dependency" );
            }
        }
        catch ( Exception e )
        {
            System.out.println( "Did not find the runtime dependency" );
        }
        
        
        String value = System.getProperty("propkey");        
        if (null != value)
        {
            System.out.println("Found the system propery passed");
        }
    }
}
