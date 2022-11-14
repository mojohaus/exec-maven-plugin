package org.codehaus.mojo.exec;

/**
 * Simple class with a main method reading a system property
 */
public class HelloSystemProperty
{
    /**
     * Print on stdout hello and the system property test.name value.
     * 
     * @param args the arguments
     */
    public static void main( String... args )
    {
        // important: do not default on empty due to testEmptySystemProperty()
        System.out.println( "Hello " + System.getProperty( "test.name" ) );
    }
}
