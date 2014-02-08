package org.codehaus.mojo.exec;

/**
 * Simple class with a main method to call from unit tests
 * 
 * @author Jerome Lacoste <jerome@coffeebreaks.org>
 * @version $Id$
 */
public class DummyMain
{
    /**
     * Prints Hello followed by each argument, then a new line. Use a space character as separator.
     * 
     * @param args
     */
    public static void main( String... args )
    {
        StringBuilder buffer = new StringBuilder( "Hello" );

        for ( String arg : args )
        {
            buffer.append( System.getProperty( "line.separator" ) ).append( arg );
        }

        System.out.println( buffer.toString() );
    }
}
