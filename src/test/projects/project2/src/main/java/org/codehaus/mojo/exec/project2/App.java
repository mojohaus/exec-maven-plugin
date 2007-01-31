package org.codehaus.mojo.exec.project2;

import org.apache.log4j.Logger;

import com.sun.java_cup.internal.runtime.Symbol;

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
            Class log = app.getClass().getClassLoader().loadClass( "org.apache.log4j.Logger" );
            if ( null != log )
            {
                System.out.println( "Found the compile dependency" );
            }
        }
        catch ( Exception e )
        {
            System.out.println( "ERROR: Did not find the compile dependency" );
        }
        
        try
        {
            Class testCase = app.getClass().getClassLoader().loadClass( "junit.framework.TestCase" );
            if ( null != testCase )
            {
                System.out.println( "ERROR: Found the test dependency (should only be test scope)" );
            }
        }
        catch ( Exception e )
        {
            System.out.println( "Did not find the test dependency" );
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
            System.out.println( "ERROR: Did not find the runtime dependency" );
        }
        
        
        String value = System.getProperty("propkey");        
        if ( "propvalue".equals( value ) )
        {
            System.out.println("Found the passed system propery");
        } else {
            System.out.println( "ERROR: Did not find the specified system property" );
        }

        value = System.getProperty("com.sun.management.jmxremote");        
        if ( value == null )
        {
            System.out.println( "Did not find the specified system property. This property can only be passed to the java mojo by using MAVEN_OPTS" );
        } else {
            System.out.println("ERROR: Unexpected: found the passed system propery. This property cannot be passed from the POM");
        }

        if ( "argument1".equals( args[0] ) ) {
            System.out.println("Found the first argument");
        } else {
            System.out.println( "ERROR: Did not find the first argument" );
        }
        if ( "argument2".equals( args[1] ) ) {
            System.out.println("Found the second argument");
        } else {
            System.out.println( "ERROR: Did not find the second argument" );
        }
    }
}
