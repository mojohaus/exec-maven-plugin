package org.codehaus.mojo.exec.project5libB;

/**
 * Used for manual integrationtest of the java goal.
 *
 */
public class App 
{
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
            System.out.println("Found the system property passed");
        }
    }
}
