package org.codehaus.mojo.exec;

/*
 * Copyright 2005 The Codehaus.
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

import org.apache.maven.plugin.MojoExecutionException;

import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.StreamConsumer;

import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;

import org.codehaus.plexus.PlexusTestCase;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.io.IOException;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
/**
 * @author Jerome Lacoste <jerome@coffeebreaks.org>
 * @version $Id$
 * @todo we depend too much on Commandline.toString()
 */
public class ExecMojoTest extends PlexusTestCase

{
    private MockExecMojo mojo;

    static class MockExecMojo extends ExecMojo  {
        public int executeResult;
        public List commandLines = new ArrayList();
        public String failureMsg;
        public Map systemProperties = new HashMap();

        protected int executeCommandLine( Commandline commandLine, 
                                          StreamConsumer stream1, StreamConsumer stream2 ) 
                  throws CommandLineException
        {
            commandLines.add( commandLine );
            if ( failureMsg != null ) {
                throw new CommandLineException( failureMsg ) ;
            }
            return executeResult;
        }

        protected String getSystemProperty( String key) {
             return (String) systemProperties.get( key );
        }
    }

    public void setUp() throws Exception {
        super.setUp();
        mojo = new MockExecMojo();
        mojo.setExecutable("mvn");
        mojo.setArguments( Arrays.asList( new String[] { "--version" } ) );
        mojo.executeResult = 0;
        mojo.setBasedir( File.createTempFile( "mvn-temp" , "txt").getParentFile() );
    }

    public void tearDown() throws Exception {
        super.tearDown();
        mojo = null;
    }

    /**
     */
    public void testRunOK() throws MojoExecutionException
    {
        mojo.execute();

        checkMojo( "mvn --version" );
    }

    /**
     * integration test...
     * - compile the Test class using mvn clean compile
     * - run the test file using java, use it to generate a file whose contains are compared to expected output
     */
    public void testRunOKWithAutoComputedClasspath() throws MojoExecutionException, Exception
    {
        String projectName = "project1";

        ExecMojo mojo = new ExecMojo();

        setUpProject( projectName, mojo );

        // compile project
        mojo.setExecutable( "mvn" );
        mojo.setWorkingDir( new File( "src/test/projects/" + projectName + "/") );
        mojo.setArguments( Arrays.asList( new String[] { "clean", "compile" } ) );

        mojo.execute();

        mojo.getLog().info ( "executed mvn clean compile" );


        // the command executes the test class
        mojo.setExecutable( "java" );
        mojo.setWorkingDir( (File) null );
        Classpath classpath = new Classpath();
        mojo.setArguments( Arrays.asList( 
            new Object[] { "-Dproject.env1=value1", 
                           "-classpath", classpath,
                           "org.codehaus.mojo.exec.test.Test", 
                           new File( "src/test/projects/" + projectName + "/target/exec/output.txt" ).getAbsolutePath(), 
                           "arg1", "arg2" }
             ) );

        mojo.execute();

        // checking the command line would involve resolving the repository
        // checkMojo( "java -cp" );

        assertFileEquals( null, getTestFile( "src/test/projects/" + projectName + "/output.txt" ),
                          getTestFile( "src/test/projects/" + projectName + "/target/exec/output.txt" ) );


        // the command executes the test class, this time specifying the dependencies
        mojo.setExecutable( "java" );
        mojo.setWorkingDir( (File) null );
        classpath = new Classpath();
        List dependencies = new ArrayList();
        dependencies.add( "commons-io:commons-io" );
        classpath.setDependencies( dependencies );
        mojo.setArguments( Arrays.asList( 
            new Object[] { "-Dproject.env1=value1", 
                           "-classpath", classpath,
                           "org.codehaus.mojo.exec.test.Test", 
                           new File( "src/test/projects/" + projectName + "/target/exec/output.txt" ).getAbsolutePath(), 
                           "arg1", "arg2" }
             ) );

        mojo.execute();

        // checking the command line would involve resolving the repository
        // checkMojo( "java -cp" );

        assertFileEquals( null, getTestFile( "src/test/projects/" + projectName + "/output.txt" ),
                          getTestFile( "src/test/projects/" + projectName + "/target/exec/output.txt" ) );
    }

    private void setUpProject( String projectName, ExecMojo mojo ) throws Exception 
    {
        MavenProjectBuilder builder = (MavenProjectBuilder) lookup( MavenProjectBuilder.ROLE );	

        ArtifactRepositoryLayout localRepositoryLayout =
            (ArtifactRepositoryLayout) lookup(
                ArtifactRepositoryLayout.ROLE, "default"
            );

        String path = "src/test/repository";        

        ArtifactRepository localRepository =
            new DefaultArtifactRepository(
                "local", "file://" + new File( path ).getAbsolutePath(),
                localRepositoryLayout
            );

        mojo.setBasedir( File.createTempFile( "mvn-temp" , "txt").getParentFile() );

        MavenProject project = builder.buildWithDependencies( getTestFile( "src/test/projects/" + projectName + "/pom.xml" ),
                                                              localRepository, null );

        mojo.setProject( project );

        mojo.setLog( new SystemStreamLog()  {
            public boolean isDebugEnabled()
            {
                return true;
            }
        });
    }

    /**
     */
    public void testRunFailure()
    {
        mojo.executeResult = 1;

        try {
           mojo.execute();
           fail( "expected failure" );
        } catch ( MojoExecutionException e ) {
            assertEquals( "Result of mvn --version execution is: '1'.", e.getMessage() );
        }

        checkMojo( "mvn --version" );
    }

    /**
     */
    public void testRunError()
    {
        mojo.failureMsg = "simulated failure";

        try {
            mojo.execute();
            fail( "expected failure" );
        } catch ( MojoExecutionException e ) {
            assertEquals( "command execution failed", e.getMessage() );
        }

        checkMojo( "mvn --version" );
    }

    /**
     */
    public void testOverrides() throws MojoExecutionException
    {
        mojo.systemProperties.put( "exec.args", "-f pom.xml" );
        mojo.execute();

        checkMojo( "mvn -f pom.xml" );
    }

    /**
     */
    public void testOverrides3() throws MojoExecutionException
    {
        mojo.systemProperties.put( "exec.args", null );
        mojo.execute();

        checkMojo( "mvn --version" );

        mojo.commandLines.clear();
        mojo.systemProperties.put( "exec.args", "" );
        mojo.execute();

        checkMojo( "mvn --version" );
    }

    private void checkMojo( String expectedCommandLine) {
        assertEquals( 1, mojo.commandLines.size() );
        assertEquals( expectedCommandLine, ((Commandline) mojo.commandLines.get(0)).toString() );
    }

    // TAKEN FROM NetbeansFreeformPluginTest - refactor ?

    /**
     * This method asserts that the two given files are equals in their
     * content.
     *
     * @param mavenRepo    Not used.
     * @param expectedFile The file that is expected.
     * @param actualFile   The file that is.
     * @throws java.io.IOException if something goes wrong.
     */
    private void assertFileEquals(
        String mavenRepo,
        File expectedFile,
        File actualFile
    )
        throws IOException
    {
        List expectedLines = getLines( mavenRepo, expectedFile );

        List actualLines = getLines( mavenRepo, actualFile );

        for ( int i = 0; i < expectedLines.size(); i++ )
        {
            String expected = expectedLines.get( i ).toString();

            if ( actualLines.size() < i )
            {
                fail(
                    "Too few lines in the actual file. Was "
                    + actualLines.size() + ", expected: "
                    + expectedLines.size()
                );
            }

            String actual = actualLines.get( i ).toString();

            assertEquals( "Checking line #" + ( i + 1 ), expected, actual );
        }

        assertTrue(
            "Unequal number of lines.",
            expectedLines.size() == actualLines.size()
        );
    }

    /**
     * This method gives the list of String in a file.
     *
     * @param mavenRepo Not used.
     * @param file      The file to be read.
     * @return The list of the lines of the file.
     * @throws java.io.IOException if something goes wrong.
     */
    private List getLines(
        String mavenRepo,
        File file
    )
        throws IOException
    {
        List lines = new ArrayList();

        BufferedReader reader = new BufferedReader( new FileReader( file ) );

        String line;

        while ( ( line = reader.readLine() ) != null )
        {
            lines.add(
                line ); //StringUtils.replace( line, "#ArtifactRepositoryPath#", mavenRepo.replace( '\\', '/' ) ) );
        }

        return lines;
    }
}
