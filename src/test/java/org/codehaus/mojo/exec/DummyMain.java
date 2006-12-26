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
    public static void main( String[] args )
    {
        StringBuffer buffer = new StringBuffer( "Hello" );

        for ( int i = 0; i < args.length; i++ )
        {
            buffer.append( System.getProperty( "line.separator" ) ).append( args[i] );
        }

        System.out.println( buffer.toString() );
    }
}
