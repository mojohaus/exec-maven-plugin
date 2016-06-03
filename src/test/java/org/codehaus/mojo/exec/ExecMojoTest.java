package org.codehaus.mojo.exec;

/*
 * Copyright 2005 The Codehaus. Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.OS;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.monitor.logging.DefaultLog;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.codehaus.plexus.util.StringOutputStream;

/**
 * @author Jerome Lacoste <jerome@coffeebreaks.org>
 * @version $Id$
 */
public class ExecMojoTest
    extends AbstractMojoTestCase
{
    /*
     * Finding a file actually on disk of the test system makes some of the tests fail.
     * Hence a purely random UUID is used to prevent this situation. 
     */
    private static final String SOME_EXECUTABLE = UUID.randomUUID().toString();

    private MockExecMojo mojo;

    static class MockExecMojo
        extends ExecMojo
    {
        public int executeResult;

        public List<CommandLine> commandLines = new ArrayList<CommandLine>();

        public String failureMsg;

        public Map systemProperties = new HashMap();

        protected int executeCommandLine( Executor exec, CommandLine commandLine, Map enviro, OutputStream out,
                                          OutputStream err )
                                              throws IOException, ExecuteException
        {
            commandLines.add( commandLine );
            if ( failureMsg != null )
            {
                throw new ExecuteException( failureMsg, executeResult );
            }
            return executeResult;
        }

        protected String getSystemProperty( String key )
        {
            return (String) systemProperties.get( key );
        }

        int getAmountExecutedCommandLines()
        {
            return commandLines.size();
        }

        CommandLine getExecutedCommandline( int index )
        {
            return ( (CommandLine) commandLines.get( index ) );
        }
    }

    public void setUp()
        throws Exception
    {
        super.setUp();
        mojo = new MockExecMojo();
        // note: most of the tests below assume that the specified
        // executable path is not fully specicied. See ExecMojo#getExecutablePath
        mojo.setExecutable( SOME_EXECUTABLE );
        mojo.setArguments( Arrays.asList( new String[] { "--version" } ) );
        mojo.executeResult = 0;
        mojo.setBasedir( File.createTempFile( "mvn-temp", "txt" ).getParentFile() );
    }

    /**
     */
    public void testRunOK()
        throws MojoExecutionException
    {
        mojo.execute();

        checkMojo( SOME_EXECUTABLE + " --version" );
    }

    /*
     * This one won't work yet public void xxtestSimpleRunPropertiesAndArguments() throws MojoExecutionException,
     * Exception { File pom = new File( getBasedir(), "src/test/projects/project1/pom.xml" ); String output = execute(
     * pom, "exec" ); System.out.println(" OUTPUT" + output + "\n\n\n"); String expectedOutput =
     * "arg.arg1\narg.arg2\nproject.env1=value1"; // FIXME should work on Windows as well assertEquals( expectedOutput,
     * output ); }
     */

    /**
     * integration test... - compile the Test class using mvn clean compile - run the test file using java, use it to
     * generate a file whose contains are compared to expected output
     */

    // public void testRunOKWithAutoComputedClasspath()
    // throws MojoExecutionException, Exception
    // {
    // String projectName = "project1";
    //
    // ExecMojo mojo = new ExecMojo();
    //
    // setUpProject( projectName, mojo );
    //
    // // compile project
    // mojo.setExecutable( SOME_EXECUTABLE );
    // mojo.setWorkingDirectory( new File( "src/test/projects/" + projectName + "/" ) );
    // mojo.setArguments( Arrays.asList( new String[]{"clean", "compile"} ) );
    //
    // mojo.execute();
    //
    // mojo.getLog().info( "executed mvn clean compile" );
    //
    // // the command executes the test class
    // mojo.setExecutable( "java" );
    // mojo.setWorkingDirectory( (File) null );
    // Classpath classpath = new Classpath();
    // mojo.setArguments( Arrays.asList( new Object[]{"-Dproject.env1=value1", "-classpath", classpath,
    // "org.codehaus.mojo.exec.test.Test",
    // new File( "src/test/projects/" + projectName + "/target/exec/output.txt" ).getAbsolutePath(), "arg1",
    // "arg2"} ) );
    //
    // mojo.execute();
    //
    // // checking the command line would involve resolving the repository
    // // checkMojo( "java -cp" );
    //
    // assertFileEquals( null, getTestFile( "src/test/projects/" + projectName + "/output.txt" ),
    // getTestFile( "src/test/projects/" + projectName + "/target/exec/output.txt" ) );
    //
    // // the command executes the test class, this time specifying the dependencies
    // mojo.setExecutable( "java" );
    // mojo.setWorkingDirectory( (File) null );
    // classpath = new Classpath();
    // List dependencies = new ArrayList();
    // dependencies.add( "commons-io:commons-io" );
    // classpath.setDependencies( dependencies );
    // mojo.setArguments( Arrays.asList( new Object[]{"-Dproject.env1=value1", "-classpath", classpath,
    // "org.codehaus.mojo.exec.test.Test",
    // new File( "src/test/projects/" + projectName + "/target/exec/output.txt" ).getAbsolutePath(), "arg1",
    // "arg2"} ) );
    //
    // mojo.execute();
    //
    // // checking the command line would involve resolving the repository
    // // checkMojo( "java -cp" );
    //
    // assertFileEquals( null, getTestFile( "src/test/projects/" + projectName + "/output.txt" ),
    // getTestFile( "src/test/projects/" + projectName + "/target/exec/output.txt" ) );
    // }

    /**
     * @return output from System.out during mojo execution
     */
    private String execute( File pom, String goal )
        throws Exception
    {

        ExecMojo mojo;
        mojo = (ExecMojo) lookupMojo( goal, pom );

        setUpProject( pom, mojo );

        MavenProject project = (MavenProject) getVariableValueFromObject( mojo, "project" );

        // why isn't this set up by the harness based on the default-value? TODO get to bottom of this!
        // setVariableValueToObject( mojo, "includeProjectDependencies", Boolean.TRUE );
        // setVariableValueToObject( mojo, "killAfter", new Long( -1 ) );

        assertNotNull( mojo );
        assertNotNull( project );

        // trap System.out
        PrintStream out = System.out;
        StringOutputStream stringOutputStream = new StringOutputStream();
        System.setOut( new PrintStream( stringOutputStream ) );
        // ensure we don't log unnecessary stuff which would interfere with assessing success of tests
        mojo.setLog( new DefaultLog( new ConsoleLogger( Logger.LEVEL_ERROR, "exec:exec" ) ) );

        try
        {
            mojo.execute();
        }
        catch ( Throwable e )
        {
            e.printStackTrace( System.err );
            fail( e.getMessage() );
        }
        finally
        {
            System.setOut( out );
        }

        return stringOutputStream.toString();
    }

    private void setUpProject( File pomFile, ExecMojo mojo )
        throws Exception
    {
        MavenProjectBuilder builder = (MavenProjectBuilder) lookup( MavenProjectBuilder.ROLE );

        ArtifactRepositoryLayout localRepositoryLayout =
            (ArtifactRepositoryLayout) lookup( ArtifactRepositoryLayout.ROLE, "default" );

        String path = "src/test/repository";

        ArtifactRepository localRepository =
            new DefaultArtifactRepository( "local", "file://" + new File( path ).getAbsolutePath(),
                                           localRepositoryLayout );

        mojo.setBasedir( File.createTempFile( "mvn-temp", "txt" ).getParentFile() );

        MavenProject project = builder.buildWithDependencies( pomFile, localRepository, null );

        // this gets the classes for these tests of this mojo (exec plugin) onto the project classpath for the test
        project.getBuild().setOutputDirectory( new File( "target/test-classes" ).getAbsolutePath() );

        mojo.setProject( project );

        mojo.setLog( new SystemStreamLog()
        {
            public boolean isDebugEnabled()
            {
                return true;
            }
        } );
    }

    // MEXEC-12, MEXEC-72
    public void testGetExecutablePath()
        throws IOException
    {

        ExecMojo realMojo = new ExecMojo();

        File workdir = new File( System.getProperty( "user.dir" ) );
        Map enviro = new HashMap();

        String myJavaPath = "target" + File.separator + "javax";
        File f = new File( myJavaPath );
        assertTrue( "file created...", f.createNewFile() );
        assertTrue( "file exists...", f.exists() );

        realMojo.setExecutable( myJavaPath );

        CommandLine cmd = realMojo.getExecutablePath( enviro, workdir );
        assertTrue( "File exists so path is absolute",
                    cmd.getExecutable().startsWith( System.getProperty( "user.dir" ) ) );

        f.delete();
        assertFalse( "file deleted...", f.exists() );
        cmd = realMojo.getExecutablePath( enviro, workdir );
        assertEquals( "File doesn't exist. Let the system find it (in that PATH?)", myJavaPath, cmd.getExecutable() );

        if ( OS.isFamilyWindows() ) // Exec Maven Plugin only supports Batch detection and PATH search on Windows
        {
            myJavaPath = "target" + File.separator + "javax.bat";
            f = new File( myJavaPath );
            assertTrue( "file created...", f.createNewFile() );
            assertTrue( "file exists...", f.exists() );

            final String comSpec = System.getenv( "ComSpec" );
            
            realMojo.setExecutable( "javax.bat" );
            cmd = realMojo.getExecutablePath( enviro, workdir );
            assertTrue( "is bat file on windows, execute using ComSpec.", cmd.getExecutable().equals( comSpec ) );

            enviro.put( "PATH", workdir.getAbsolutePath() + File.separator + "target" );
            cmd = realMojo.getExecutablePath( enviro, workdir );
            assertTrue( "is bat file on windows' PATH, execute using ComSpec.", cmd.getExecutable().equals( comSpec ) );
            f.delete();
            assertFalse( "file deleted...", f.exists() );
            
            // ISSUE-42
            String executableInPath = "javax";
            realMojo.setExecutable(executableInPath);
            cmd = realMojo.getExecutablePath( enviro, workdir );
            assertTrue( "file without extension is supposed in PATH", executableInPath.equals(cmd.getExecutable()));
            
        }
    }
    

    public void testRunFailure()
    {
        mojo.executeResult = 1;

        try
        {
            mojo.execute();
            fail( "expected failure" );
        }
        catch ( MojoExecutionException e )
        {
            assertEquals( "Result of " + mojo.getExecutedCommandline( 0 ) + " execution is: '1'.", e.getMessage() );
        }

        checkMojo( SOME_EXECUTABLE + " --version" );
    }

    public void testRunError()
    {
        mojo.failureMsg = "simulated failure";

        try
        {
            mojo.execute();
            fail( "expected failure" );
        }
        catch ( MojoExecutionException e )
        {
            assertEquals( "Command execution failed.", e.getMessage() );
        }

        checkMojo( SOME_EXECUTABLE + " --version" );
    }

    public void testOverrides()
        throws MojoExecutionException
    {
        mojo.systemProperties.put( "exec.args", "-f pom.xml" );
        mojo.execute();

        checkMojo( SOME_EXECUTABLE + " -f pom.xml" );
    }

    public void testOverrides3()
        throws MojoExecutionException
    {
        mojo.systemProperties.put( "exec.args", null );
        mojo.execute();

        checkMojo( SOME_EXECUTABLE + " --version" );

        mojo.commandLines.clear();
        mojo.systemProperties.put( "exec.args", "" );
        mojo.execute();

        checkMojo( SOME_EXECUTABLE + " --version" );
    }

    public void testIsResultCodeAFailure()
    {
        ExecMojo execMojo = new ExecMojo();
        assertTrue( execMojo.isResultCodeAFailure( 1 ) );
        assertFalse( execMojo.isResultCodeAFailure( 0 ) );

        execMojo.setSuccessCodes( new Integer[0] );
        assertTrue( execMojo.isResultCodeAFailure( 1 ) );
        assertFalse( execMojo.isResultCodeAFailure( 0 ) );

        execMojo.setSuccessCodes( new Integer[] { Integer.valueOf( "2" ), Integer.valueOf( "5" ) } );
        assertTrue( execMojo.isResultCodeAFailure( 0 ) );
        assertTrue( execMojo.isResultCodeAFailure( 10 ) );
        assertFalse( execMojo.isResultCodeAFailure( 2 ) );
        assertFalse( execMojo.isResultCodeAFailure( 5 ) );
    }

    // MEXEC-81
    public void testParseCommandlineOSWin()
        throws Exception
    {
        ExecMojo execMojo = new ExecMojo();
        final String javaHome = "C:\\Java\\jdk1.5.0_15";
        // can only be set by expression or plugin-configuration
        setVariableValueToObject( execMojo, "commandlineArgs", javaHome );
        String[] args = execMojo.parseCommandlineArgs();
        assertEquals( javaHome, args[0] );
    }

    private void checkMojo( String expectedCommandLine )
    {
        assertEquals( 1, mojo.getAmountExecutedCommandLines() );
        CommandLine commandline = mojo.getExecutedCommandline( 0 );
        // do NOT depend on Commandline toString()
        assertEquals( expectedCommandLine, getCommandLineAsString( commandline ) );
    }

    private String getCommandLineAsString( CommandLine commandline )
    {
        // for the sake of the test comparisons, cut out the eventual
        // cmd /c *.bat conversion
        String result = commandline.getExecutable();
        boolean isCmd = false;
        if ( OS.isFamilyWindows() && result.equals( "cmd" ) )
        {
            result = "";
            isCmd = true;
        }
        String[] arguments = commandline.getArguments();
        for ( int i = 0; i < arguments.length; i++ )
        {
            String arg = arguments[i];
            if ( isCmd && i == 0 && "/c".equals( arg ) )
            {
                continue;
            }
            if ( isCmd && i == 1 && arg.endsWith( ".bat" ) )
            {
                arg = arg.substring( 0, arg.length() - ".bat".length() );
            }
            result += ( result.length() == 0 ? "" : " " ) + arg;
        }
        return result;
    }

}
