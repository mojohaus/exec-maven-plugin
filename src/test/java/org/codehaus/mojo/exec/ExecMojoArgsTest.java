package org.codehaus.mojo.exec;

import java.io.File;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.maven.embedder.MavenEmbedder;
import org.apache.maven.embedder.MavenEmbedderConsoleLogger;
import org.apache.maven.embedder.MavenEmbedderLogger;
import org.apache.maven.monitor.event.DefaultEventMonitor;
import org.apache.maven.monitor.event.EventMonitor;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.apache.maven.wagon.events.TransferListener;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.codehaus.plexus.util.StringOutputStream;

/**
 * This class is intentended to test the commandline parsing facilities of the {@link AbstractExecMojo} class
 * 
 * @author Philippe Jacot (PJA)
 */
public class ExecMojoArgsTest extends AbstractMojoTestCase
{
    private static final String[] ARGS = new String[] { "Arg1", "Arg2a Arg2b" };

    private static final String LS = System.getProperty( "line.separator" );

    /**
     * The embedder used to execute the testcase.
     */
    private MavenEmbedder maven;

    protected void setUp() throws Exception
    {
        super.setUp();

        maven = new MavenEmbedder();

        maven.setLogger( new MavenEmbedderConsoleLogger() );
        maven.setClassLoader( Thread.currentThread().getContextClassLoader() );

        maven.start();

    }

    /**
     * Check that arguments are successfully parsed from the exec.args system property. We load the config from a pom
     * file and fill up the {@link MavenProject} property ourselves and then set the arguments
     */
    public void testRunWithArgs() throws Exception
    {
        List goals = Collections.singletonList( "exec:java" );
        File executionRootDirectory = new File( getBasedir() );
        EventMonitor eventDispatcher =
            new DefaultEventMonitor( new ConsoleLogger( Logger.LEVEL_ERROR, "AbstractExecMojoLogger" ) );
        Properties properties = new Properties();
        String args = "";
        for ( int i = 0; i < ARGS.length; i++ )
        {
            args += '\"' + ARGS[i] + "\" ";
        }
        properties.put( "exec.args", args );

        MavenProject project = maven.readProject( new File( getBasedir(), "src/test/projects/project8/pom.xml" ) );

        String resultString = execute( project, goals, eventDispatcher, null, properties, executionRootDirectory );

        String expectedResult = "Hello" + LS;
        for ( int i = 0; i < ARGS.length; i++ )
        {
            expectedResult += ARGS[i] + LS;
        }
        assertEquals( expectedResult, resultString );
    }

    private String execute( MavenProject project, List goals, EventMonitor eventMonitor,
                            TransferListener transferListener, Properties properties, File executionRootDirectory )
    {
        PrintStream out = System.out;
        StringOutputStream requestOut = new StringOutputStream();
        // Only allow errors and above the return string
        // Otherwise the expected Result is hard to predict
        int old_loglevel = maven.getLogger().getThreshold();
        maven.getLogger().setThreshold( MavenEmbedderLogger.LEVEL_ERROR );

        try
        {
            System.setOut( new PrintStream( requestOut ) );

            maven.execute( project, goals, eventMonitor, transferListener, properties, executionRootDirectory );
        }
        catch ( Exception e )
        {
            e.printStackTrace( System.err );
            fail( e.getMessage() );
        }
        finally
        {
            System.setOut( out );
        }

        maven.getLogger().setThreshold( old_loglevel );
        return requestOut.toString();
    }

}
