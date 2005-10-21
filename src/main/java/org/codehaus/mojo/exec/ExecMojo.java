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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.DefaultConsumer;
import org.codehaus.plexus.util.cli.StreamConsumer;

import java.io.File;
import java.util.List;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.ArrayList;

/**
 * A Plugin for executing external programs.
 *
 * @goal exec
 * @requiresDependencyResolution
 * @description Program Execution plugin
 * @author Jerome Lacoste <jerome@coffeebreaks.org>
 * @version $Id:$
 */
public class ExecMojo extends AbstractMojo {

    /**
     * @parameter
     */
    private String executable;
  
    /**
     * @parameter
     */
    private File workingDirectory;

    /**
     * @parameter
     */
    private List arguments;
   
    /**
     * @parameter expression="${project}"
     * @required
     */
    // private MavenProject project;

    /**
     * @parameter expression="${basedir}"
     * @required
     * @readonly
     */
    private File basedir;

    /**
     * priority in the execute method will be to use System properties arguments over the pom specification.
     */
    public void execute() throws MojoExecutionException {

        if ( basedir == null ) {
            throw new IllegalStateException( "basedir is null. Should not be possible." );  
        }


        String executableProp = getSystemProperty( "exec.executable" );
         
        if ( ! isEmpty(executableProp) ) {

            getLog().debug( "got an executable from system properties: " + executableProp );

            executable = executableProp;

        }
         
        String workingDirProp = getSystemProperty( "exec.workingdir" );
         
        if ( ! isEmpty(workingDirProp) ) {

            getLog().debug( "got a workingdir from system properties: " + workingDirProp );

            workingDirectory = new File(workingDirProp);

        }

        String argsProp = getSystemProperty( "exec.args" );

        if ( ! isEmpty(argsProp) ) {

            getLog().debug( "got arguments from system properties: " + argsProp );

            arguments = new ArrayList();

            StringTokenizer strtok = new StringTokenizer( argsProp, " " );
            while ( strtok.hasMoreTokens() ) {
                arguments.add( strtok.nextToken() );
            }
        }
      
        if (getLog().isDebugEnabled()) {
            getLog().debug( "executable: " + executable );
            getLog().debug( "basedir: " + basedir );
            getLog().debug( "workingDirectory: " + workingDirectory );
            if ( arguments != null ) {
                for ( Iterator i = arguments.iterator() ; i.hasNext() ; ) {
                    getLog().debug( "argument: " + (String) i.next() );
                }
            } else {
                getLog().debug( "no arguments specified" );
            }
        }

        Commandline commandLine = new Commandline();

        commandLine.setExecutable( executable );

        if ( arguments != null ) {
            for ( Iterator it = arguments.iterator() ; it.hasNext() ; ) {
                commandLine.createArgument().setLine( it.next().toString() );
            }
        }

        if ( workingDirectory != null ) {

            commandLine.setWorkingDirectory( workingDirectory.getAbsolutePath() );

        } else {

            commandLine.setWorkingDirectory( basedir.getAbsolutePath() );

        }

        StreamConsumer consumer = new DefaultConsumer();

        try
        {
            int result = executeCommandLine( commandLine, consumer, consumer );
            
            if ( result != 0 )
            {
                throw new MojoExecutionException("Result of " + commandLine + " execution is: \'" + result + "\'." );
            }
        }
        catch ( CommandLineException e )
        {
            throw new MojoExecutionException( "command execution failed", e );
        }
    }

    private static boolean isEmpty( String string ) {
        return string == null || string.length() == 0;
    }

    //
    // methods used for tests purposes - allow mocking and simulate automatic setters
    //

    protected int executeCommandLine( Commandline commandLine, 
                                      StreamConsumer stream1, StreamConsumer stream2 ) 
             throws CommandLineException {
        return CommandLineUtils.executeCommandLine( commandLine, stream1, stream2 );
    }

    void setExecutable( String executable ) {
        this.executable = executable;
    }

    void setWorkingDir( String workingDir ) {
        this.workingDirectory = new File( workingDir );
    }

    void setArguments( List arguments ) {
        this.arguments = arguments;
    }

    void setBasedir( File basedir ) {
        this.basedir = basedir;
    }

    protected String getSystemProperty( String key ) {
        return System.getProperty( key );
    }
}
