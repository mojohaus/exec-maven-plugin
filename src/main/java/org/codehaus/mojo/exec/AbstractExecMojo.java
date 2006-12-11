package org.codehaus.mojo.exec;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * This class is used for parsing the arguments for multiple mojos of the exec plugin. It is intended to unify the way
 * commandline arguments are passed to the mojos via a system property.
 * 
 * @author Philippe Jacot (PJA)
 */
public abstract class AbstractExecMojo extends AbstractMojo
{

    /**
     * Arguments for the executed program
     * 
     * @parameter expression="${exec.args}"
     */
    private String commandline_args;

    private static final char PARAMETER_DELIMITER = ' ';

    private static final char STRING_WRAPPER = '"';

    private static final char ESCAPE_CHAR = '\\';

    /**
     * Parses the argument string given by the user. Strings are recognized as everything between STRING_WRAPPER.
     * PARAMETER_DELIMITER is ignored inside a string. STRING_WRAPPER and PARAMETER_DELIMITER can be escaped using
     * ESCAPE_CHAR.
     * 
     * @return Array of String representing the arguments
     * @throws MojoExecutionException
     *             for wrong formatted arguments
     */
    protected String[] parseCommandlineArgs() throws MojoExecutionException
    {
        if ( commandline_args == null )
            return null;

        boolean inString = false;
        String arguments = commandline_args.trim();

        List argumentList = new LinkedList();

        int chr = 0;
        char curChar;
        StringBuffer arg = new StringBuffer();
        while ( chr < arguments.length() )
        {
            curChar = arguments.charAt( chr );
            if ( curChar == ESCAPE_CHAR )
            {
                // A char is escaped, replace and skip one char
                if ( arguments.length() == chr + 1 )
                {
                    // ESCAPE_CHAR is the last character... this should be an error (?)
                    // char is just ignored by now
                }
                else
                {
                    // In a string only STRING_WRAPPER is escaped
                    if ( !inString || arguments.charAt( chr + 1 ) == STRING_WRAPPER )
                    {
                        chr++;
                    }

                    arg.append( arguments.charAt( chr ) );
                }
            }
            else if ( curChar == PARAMETER_DELIMITER && !inString )
            {
                // Next parameter begins here
                argumentList.add( arg.toString() );
                arg.delete( 0, arg.length() );
            }
            else if ( curChar == STRING_WRAPPER )
            {
                // Entering or leaving a string
                inString = !inString;
            }
            else
            {
                // Append this char to the current parameter
                arg.append( curChar );
            }

            chr++;
        }
        if ( inString )
        {
            // Error string not terminated
            throw new MojoExecutionException( "args contains not properly formatted string" );
        }
        else
        {
            // Append last parameter
            argumentList.add( arg.toString() );
        }

        Iterator it = argumentList.listIterator();
        String[] result = new String[argumentList.size()];
        int index = 0;
        while ( it.hasNext() )
        {
            result[index] = (String) it.next();
            index++;
        }

        getLog().debug( "Args:" );
        it = argumentList.listIterator();
        while ( it.hasNext() )
        {
            getLog().debug( " <" + (String) it.next() + ">" );
        }
        getLog().debug( " parsed from <" + commandline_args + ">" );

        return result;
    }

    protected boolean hasCommandlineArgs()
    {
        return ( commandline_args != null );
    }
}
