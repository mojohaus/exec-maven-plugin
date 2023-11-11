package org.codehaus.mojo.exec;

/*
 * Copyright 2005-2006 The Codehaus.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.monitor.logging.DefaultLog;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * @author Jerome Lacoste
 * @version $Id$
 */
public class ExecJavaMojoTest
    extends AbstractMojoTestCase
{
    @Mock
    private MavenSession session;
    
    private static final File LOCAL_REPO = new File( "src/test/repository" );

    private static final int JAVA_VERSION_MAJOR =
        Integer.parseInt( System.getProperty( "java.version" ).replaceFirst( "[.].*", "" ) ); 

    /*
     * This one won't work yet public void xxtestSimpleRunPropertiesAndArguments() throws MojoExecutionException,
     * Exception { File pom = new File( getBasedir(), "src/test/projects/project2/pom.xml" ); String output = execute(
     * pom, "java" ); System.out.println(output); assertEquals( -1, output.trim().indexOf( "ERROR" ) ); }
     */

    /**
     * Check that a simple execution with no arguments and no system properties produces the expected result.<br>
     * We load the config from a pom file and fill up the MavenProject property ourselves
     * 
     * @throws Exception if any exception occurs
     */
    public void testSimpleRun()
        throws Exception
    {
        File pom = new File( getBasedir(), "src/test/projects/project4/pom.xml" );

        String output = execute( pom, "java" );

        assertEquals( "Hello" + System.getProperty( "line.separator" ), output );
    }

    /**
     * MEXEC-10 Check that an execution with no arguments and an system property with no value produces the expected
     * result<br>
     * We load the config from a pom file and fill up the MavenProject property ourselves
     * 
     * @throws Exception if any exception occurs
     */
    public void testEmptySystemProperty()
        throws Exception
    {
        File pom = new File( getBasedir(), "src/test/projects/project5/pom.xml" );

        assertNull( "System property not yet created", System.getProperty( "test.name" ) );

        assertEquals( "Hello " + System.lineSeparator(), execute( pom, "java" ) );

        // ensure we get back in the original state and didn't leak the execution config
        assertNull( "System property not yet created", System.getProperty( "test.name" ) );
    }

    /**
     * Check that an execution that throws propagates the cause of the failure into the output
     * and correctly unwraps the InvocationTargetException.
     *
     * @author Lukasz Cwik
     * @throws Exception if any exception occurs
     */
    public void testRunWhichThrowsExceptionIsNotWrappedInInvocationTargetException()
        throws Exception
    {
      File pom = new File( getBasedir(), "src/test/projects/project15/pom.xml" );

      try
      {
          execute( pom, "java" );

          fail( "Expected Exception to be thrown but none was thrown" );
      }
      catch ( Throwable e )
      {
          assertTrue( e instanceof MojoExecutionException );

          assertTrue( e.getCause() instanceof IOException );

          assertEquals( "expected IOException thrown by test", e.getCause().getMessage() );
      }
    }

    /**
     * MEXEC-29 exec:java throws NPE if the mainClass main method has not a correct signature
     */
    // Moved this test to src/it/mexec-29 (integration test)
    // cause it will fail. This is based of trying to
    // using dependencies (commons-logging:1.0.4:jar; commons-io:commons-is:1.1) which will be resolved
    // against maven central which will not work always in particular
    // when maven central is proxied via a repository manager.
    // This could be solved by using the local settings.xml
    // file of the user, but the tests don't use it.
    // Apart from that if the class does not exist as in this test
    // the real execution of the plugin will result in a ClassNotFoundException
    // like this:
    // [DEBUG] Adding project dependency artifact: commons-io to classpath
    // [DEBUG] Adding project dependency artifact: commons-logging to classpath
    // [DEBUG] joining on thread Thread[org.codehaus.mojo.exec.NoMain.main(),5,org.codehaus.mojo.exec.NoMain]
    // [WARNING]
    // java.lang.ClassNotFoundException: org.codehaus.mojo.exec.NoMain
    // at java.net.URLClassLoader$1.run(URLClassLoader.java:366)
    // at java.net.URLClassLoader$1.run(URLClassLoader.java:355)
    // at java.security.AccessController.doPrivileged(Native Method)
    // at java.net.URLClassLoader.findClass(URLClassLoader.java:354)
    // at java.lang.ClassLoader.loadClass(ClassLoader.java:423)
    // at java.lang.ClassLoader.loadClass(ClassLoader.java:356)
    // at org.codehaus.mojo.exec.ExecJavaMojo$1.run(ExecJavaMojo.java:293)
    // at java.lang.Thread.run(Thread.java:722)

    /*
     * public void testIncorrectMainMethodSignature() throws Exception { File pom = new File( getBasedir(),
     * "src/test/projects/project12/pom.xml" ); try { String output = execute( pom, "java" ); } catch
     * (MojoExecutionException e) { assertTrue( stringContains( e.getMessage(),
     * "The specified mainClass doesn't contain a main method with appropriate signature." ) ); } }
     */

    // this test doesn't work as the classpath passed to the project when executing the POM isn't the same as when maven
    // is executed from within the project dir
    // Should be moved as an integration-test
    /*
     * public void testSetClasspathScopeToTest() throws Exception { File pom = new File( getBasedir(),
     * "src/test/projects/project14/pom.xml" ); String output = execute( pom, "java" ); assertEquals( "Hello" +
     * System.getProperty( "line.separator" ), output ); }
     */

    /**
     * For cases where the Java code spawns Threads and main returns soon. See
     * <a href="http://jira.codehaus.org/browse/MEXEC-6">MEXEC-6</a>.
     * 
     * @throws Exception if any exception occurs
     */
    public void testWaitNoDaemonThreads()
        throws Exception
    {
        File pom = new File( getBasedir(), "src/test/projects/project7/pom.xml" );

        String output = execute( pom, "java" );

        assertEquals( MainWithThreads.ALL_EXITED, output.trim() );
    }

    /**
     * For cases where the Java code spawns Threads and main returns soon, but code contains non interruptible threads.
     * User is required to timeout the execution, otherwise it will hang. See
     * <a href="http://jira.codehaus.org/browse/MEXEC-15">MEXEC-15</a>.
     * 
     * @throws Exception if any exception occurs
     */
    public void testWaitNonInterruptibleDaemonThreads()
        throws Exception
    {
        File pom = new File( getBasedir(), "src/test/projects/project9/pom.xml" );

        String output = execute( pom, "java" );

        assertEquals( MainWithThreads.TIMER_IGNORED, output.trim() );
    }

    /**
     * See <a href="http://jira.codehaus.org/browse/MEXEC-15">MEXEC-15</a>,
     * <a href="https://github.com/mojohaus/exec-maven-plugin/issues/391">GitHub-391</a>.
     * <p>
     * FIXME: This sometimes fails with {@code unit.framework.ComparisonFailure: expected:<...>; but was:<...3(f)>}.
     * 
     * @throws Exception if any exception occurs
     */
    public void testUncooperativeThread()
        throws Exception
    {
        // FIXME:
        //   This will fail the test, because Assume is a JUnit 4 thing, but we are running in JUnit 3 mode, because
        //   AbstractMojoTestCase extends PlexusTestCase extends TestCase. The latter is a JUnit 3 compatibility class.
        //   If we would simply use JUnit 4 annotations, conditional ignores via Assume would just work correctly in
        //   Surefire and IDEs. We could than have two dedicated test cases, one for each JDK 20+ and one for older
        //   versions. In JUnit 5, we could even conveniently use @EnabledOnJre.
        // Assume.assumeTrue( JAVA_VERSION_MAJOR < 20 );

        File pom = new File( getBasedir(), "src/test/projects/project10/pom.xml" );
        String output = execute( pom, "java" );
        // note: execute() will wait a little bit before returning the output,
        // thereby allowing the stop()'ed thread to output the final "(f)".
        if ( JAVA_VERSION_MAJOR < 20 )
        {
            assertEquals( MainUncooperative.SUCCESS, output.trim() );
        }
        else
        {
            assertEquals( MainUncooperative.INTERRUPTED_BUT_NOT_STOPPED, output.trim() );
        }
    }

    /**
     * See <a href="http://jira.codehaus.org/browse/MEXEC-17">MEXEC-17</a>.
     */
    /**
     * This test doesn't work because the sun.tools.javac.Main class referenced in the project pom is found even if the
     * system scope dependencies are not added by the plugin. The class was probably loaded by another plugin ?! To fix
     * the test we have to: - maybe use a different system scope dependency/class to load. - find a different way to
     * test. When ran manually, the test works though (i.e. removing the code that manually adds the system scope
     * dependencies make the test fail). public void testSystemDependencyFound() throws Exception { File pom = new File(
     * getBasedir(), "src/test/projects/project11/pom.xml" ); String output = execute( pom, "java" ); assertEquals(
     * FindClassInClasspath.FOUND_ALL, output.trim() ); }
     */

    /**
     * Test the commandline parsing facilities of the {@link AbstractExecMojo} class
     * @throws Exception if any exception occurs
     */
    public void testRunWithArgs()
        throws Exception
    {

        String resultString = execute( new File( getBasedir(), "src/test/projects/project8/pom.xml" ), "java" );

        String LS = System.getProperty( "line.separator" );
        String expectedResult = "Hello" + LS + "Arg1" + LS + "Arg2a Arg2b" + LS;
        assertEquals( expectedResult, resultString );
    }

    /**
     * Ensures that classpath can be filtered (exclude from plugin deps or project deps) to resolve conflicts.
     * @throws Exception if something unexpected occurs.
     */
    public void testExcludedClasspathElement() throws Exception
    {
        String LS = System.getProperty( "line.separator" );

        // slf4j-simple
        {
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            execute(
                    new File( getBasedir(), "src/test/projects/project16/pom.xml" ), "java",
                    stdout, stderr);
            assertEquals( "org.slf4j.impl.SimpleLogger", stdout.toString().trim() );
            assertEquals(
                    "[org.codehaus.mojo.exec.Slf4jMain.main()] INFO org.codehaus.mojo.exec.Slf4jMain - hello[]" + LS,
                    stderr.toString() );
        }

        // slf4j-jdk14
        {
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            execute(
                    new File( getBasedir(), "src/test/projects/project17/pom.xml" ), "java",
                    stdout, stderr);
            assertEquals( "org.slf4j.impl.JDK14LoggerAdapter", stdout.toString().trim() );
            final String stderrString = stderr.toString(); // simpler check, just validate it is not simple output
            assertTrue( stderrString.contains( " org.codehaus.mojo.exec.Slf4jMain main" ) );
            assertTrue( stderrString.contains( ": hello[]" ) );
        }
    }

    /**
     * Ensure all project properties can be forwarded to the execution as system properties.
     *
     * @throws Exception if any exception occurs
     */
    public void testProjectProperties()
            throws Exception
    {
        File pom = new File( getBasedir(), "src/test/projects/project18/pom.xml" );

        String output = execute( pom, "java" );

        assertEquals( "Hello project18 project" + System.lineSeparator(), output );
    }

    /**
     * @return output from System.out during mojo execution
     */
    private String execute( File pom, String goal )
        throws Exception
    {
        return execute( pom, goal, new ByteArrayOutputStream(), new ByteArrayOutputStream() );
    }

    /**
     * @return output from System.out during mojo execution
     */
    private String execute( File pom, String goal, ByteArrayOutputStream stringOutputStream, OutputStream stderr )
        throws Exception
    {

        ExecJavaMojo mojo;
        mojo = (ExecJavaMojo) lookupMojo( goal, pom );

        setUpProject( pom, mojo );

        MavenProject project = (MavenProject) getVariableValueFromObject( mojo, "project" );

        // why isn't this set up by the harness based on the default-value? TODO get to bottom of this!
        setVariableValueToObject( mojo, "includeProjectDependencies", Boolean.TRUE );
        setVariableValueToObject( mojo, "killAfter", (long) -1 );
        setVariableValueToObject( mojo, "cleanupDaemonThreads", Boolean.TRUE );
        setVariableValueToObject( mojo, "classpathScope", "compile" );

        assertNotNull( mojo );
        assertNotNull( project );

        // trap System.out
        PrintStream out = System.out;
        PrintStream err = System.err;
        System.setOut( new PrintStream( stringOutputStream ) );
        System.setErr( new PrintStream( stderr ) );
        // ensure we don't log unnecessary stuff which would interfere with assessing success of tests
        mojo.setLog( new DefaultLog( new ConsoleLogger( Logger.LEVEL_ERROR, "exec:java" ) ) );

        try
        {
            mojo.execute();
        }
        finally
        {
            // see testUncooperativeThread() for explaination
            Thread.sleep( 300 ); // time seems about right
            System.setOut( out );
            System.setErr( err );
        }

        return stringOutputStream.toString();
    }

    private void setUpProject( File pomFile, AbstractMojo mojo )
        throws Exception
    {
        super.setUp();
        
        MockitoAnnotations.initMocks( this );
        
        ProjectBuildingRequest buildingRequest = mock( ProjectBuildingRequest.class );
        when( session.getProjectBuildingRequest() ).thenReturn( buildingRequest );
        RepositorySystemSession repositorySession = new DefaultRepositorySystemSession();
        when( buildingRequest.getRepositorySession() ).thenReturn( repositorySession );
        
        ProjectBuilder builder = lookup( ProjectBuilder.class );

        MavenProject project = builder.build( pomFile, buildingRequest ).getProject();
        // this gets the classes for these tests of this mojo (exec plugin) onto the project classpath for the test
        project.getBuild().setOutputDirectory( new File( "target/test-classes" ).getAbsolutePath() );
        setVariableValueToObject( mojo, "project", project );
    }
}
