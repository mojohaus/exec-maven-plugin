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

import org.apache.maven.project.MavenProject;
import org.apache.maven.artifact.Artifact;

import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.DefaultConsumer;
import org.codehaus.plexus.util.cli.StreamConsumer;

import org.apache.maven.artifact.resolver.filter.AndArtifactFilter;
import org.apache.maven.artifact.resolver.filter.IncludesArtifactFilter;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * A Plugin for executing external programs.
 *
 * @goal exec
 * @requiresDependencyResolution
 * @description Program Execution plugin
 * @author Jerome Lacoste <jerome@coffeebreaks.org>
 * @version $Id$
 */
public class ExecMojo extends AbstractMojo {

   /**
    * @parameter expression="${skip}" default-value="false"
    */
   private boolean skip;

    /**
     * @parameter expression="${exec.executable}"
     * @required
     */
    private String executable;
  
    /**
     * @parameter expression="${exec.workingdir}
     */
    private File workingDirectory;

    /**
     * Can be of type <code>&lt;argument&gt;</code> or <code>&lt;classpath&gt;</code>
     * Can be overriden using "exec.args" env. variable
     * @parameter
     */
    public List arguments;
   
    /**
     * @parameter expression="${project}"
     * @required
     */
    private MavenProject project;

    /**
     * @parameter expression="${basedir}"
     * @required
     */
    private File basedir;

    /**
     * priority in the execute method will be to use System properties arguments over the pom specification.
     */
    public void execute() throws MojoExecutionException {

        if ( skip )
        {  
           getLog().info( "skipping execute as per configuraion" );
           return;
        }

        if ( basedir == null ) {
            throw new IllegalStateException( "basedir is null. Should not be possible." );  
        }

        String argsProp = getSystemProperty( "exec.args" );

        List commandArguments = new ArrayList();

        if ( ! isEmpty(argsProp) ) {

            getLog().debug( "got arguments from system properties: " + argsProp );

            StringTokenizer strtok = new StringTokenizer( argsProp, " " );
            while ( strtok.hasMoreTokens() ) {
                commandArguments.add( strtok.nextToken() );
            }
        } else {
            if ( arguments != null ) {
                for ( int i = 0; i < arguments.size(); i++) {
                    Object argument = arguments.get( i );
                    String arg;
                    if ( argument instanceof Classpath ) {
                         Classpath classpath = (Classpath) argument;
                         Collection artifacts = project.getArtifacts();
                         if ( classpath.getDependencies() != null ) {
                             artifacts = filterArtifacts( artifacts, classpath.getDependencies() );
                         }
                         arg = computeClasspath( artifacts );
                    } else {
                         arg = argument.toString();
                    }
                    commandArguments.add( arg );
                }
            }
        }
      
        Commandline commandLine = new Commandline();

        commandLine.setExecutable( executable );

        for ( Iterator it = commandArguments.iterator() ; it.hasNext() ; ) {
            commandLine.createArgument().setValue( it.next().toString() );
        }

        if ( workingDirectory != null ) {

            commandLine.setWorkingDirectory( workingDirectory.getAbsolutePath() );

        } else {

            commandLine.setWorkingDirectory( basedir.getAbsolutePath() );

        }

        // FIXME what about redirecting the output to getLog() ??
        // what about consuming the input just to be on the safe side ?
        // what about allowing parametrization of the class name that acts as consumer?
        StreamConsumer consumer = new StreamConsumer() {
            public void consumeLine ( String line ) {
                getLog().info( line );
            }
        };

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

    private String computeClasspath( Collection artifacts ) {
        StringBuffer theClasspath = new StringBuffer();

        for ( Iterator it = artifacts.iterator(); it.hasNext(); ) {
            if ( theClasspath.length() > 0 ) {
                theClasspath.append( File.pathSeparator );
            }
            Artifact artifact = (Artifact) it.next();
            getLog().debug( "dealing with " + artifact );
            theClasspath.append( artifact.getFile().getAbsolutePath() );
        }
        // FIXME check project current phase?
        // we should probably add the output and testoutput dirs based on the Project's phase
        if ( true ) {
             if ( theClasspath.length() > 0 ) {
                  theClasspath.append( File.pathSeparator );
             }
             theClasspath.append( project.getBuild().getOutputDirectory() );
        }
        if ( false ) {
             if ( theClasspath.length() > 0 ) {
                  theClasspath.append( File.pathSeparator );
             }
             theClasspath.append( project.getBuild().getTestOutputDirectory() );
        }

        return theClasspath.toString();
    }

    private Collection filterArtifacts( Collection artifacts, Collection dependencies ) {
        AndArtifactFilter filter = new AndArtifactFilter();
        
        filter.add( new IncludesArtifactFilter( new ArrayList( dependencies ) ) ); // gosh

        StringBuffer theClasspath = new StringBuffer();

        List filteredArtifacts = new ArrayList();
        for ( Iterator it = artifacts.iterator(); it.hasNext(); ) {
            Artifact artifact = (Artifact) it.next();
            if ( filter.include( artifact ) ) {
                getLog().debug( "filtering in " + artifact );
                filteredArtifacts.add( artifact );
            }
        }
        return filteredArtifacts;
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

    void setWorkingDirectory( String workingDir ) {
        setWorkingDirectory( new File( workingDir ) );
    }

    void setWorkingDirectory( File workingDir ) {
        this.workingDirectory = workingDir;
    }

    void setArguments( List arguments ) {
        this.arguments = arguments;
    }

    void setBasedir( File basedir ) {
        this.basedir = basedir;
    }

    void setProject( MavenProject project ) {
        this.project = project;
    }

    protected String getSystemProperty( String key ) {
        return System.getProperty( key );
    }
}
