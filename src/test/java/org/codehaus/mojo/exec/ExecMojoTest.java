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

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.File;
import java.io.IOException;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Jerome Lacoste <jerome@coffeebreaks.org>
 * @version $Id:$
 * @todo we depend too much on Commandline.toString()
 */
public class ExecMojoTest extends TestCase
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


    public void setUp() throws IOException {
        mojo = new MockExecMojo();
        mojo.setExecutable("m2");
        mojo.setArguments( Arrays.asList( new String[] { "--version" } ) );
        mojo.executeResult = 0;
        mojo.setBasedir( File.createTempFile( "m2-temp" , "txt").getParentFile() );
    }

    public void tearDown() {
        mojo = null;
    }

    /**
     */
    public void testRunOK() throws MojoExecutionException
    {
        mojo.execute();

        checkMojo( "m2 --version" );
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
            assertEquals( "Result of m2 --version execution is: '1'.", e.getMessage() );
        }

        checkMojo( "m2 --version" );
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

        checkMojo( "m2 --version" );
    }

    /**
     */
    public void testOverrides() throws MojoExecutionException
    {
        mojo.systemProperties.put( "exec.args", "-f pom.xml" );
        mojo.execute();

        checkMojo( "m2 -f pom.xml" );
    }

    /**
     */
    public void testOverrides2() throws MojoExecutionException
    {
        mojo.systemProperties.put( "exec.executable", "/bin/m2" );
        mojo.execute();

        checkMojo( "/bin/m2 --version" );
    }

    /**
     */
    public void testOverrides3() throws MojoExecutionException
    {
        mojo.systemProperties.put( "exec.args", null );
        mojo.execute();

        checkMojo( "m2 --version" );

        mojo.commandLines.clear();
        mojo.systemProperties.put( "exec.args", "" );
        mojo.execute();

        checkMojo( "m2 --version" );
    }

    private void checkMojo( String expectedCommandLine) {
        assertEquals( 1, mojo.commandLines.size() );
        assertEquals( expectedCommandLine, ((Commandline) mojo.commandLines.get(0)).toString() );
    }
}
