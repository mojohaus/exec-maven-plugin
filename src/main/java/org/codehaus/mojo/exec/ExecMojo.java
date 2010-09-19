package org.codehaus.mojo.exec;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.OS;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.AndArtifactFilter;
import org.apache.maven.artifact.resolver.filter.IncludesArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineUtils;

/**
 * A Plugin for executing external programs.
 * 
 * @author Jerome Lacoste <jerome@coffeebreaks.org>
 * @version $Id$
 * @goal exec
 * @requiresDependencyResolution test
 * @since 1.0
 */
public class ExecMojo
    extends AbstractExecMojo
{
    /**
     * The executable. Can be a full path or a the name executable. In the latter case, the executable must be in the
     * PATH for the execution to work.
     * 
     * @parameter expression="${exec.executable}"
     * @required
     * @since 1.0
     */
    private String executable;

    /**
     * The current working directory. Optional. If not specified, basedir will be used.
     * 
     * @parameter expression="${exec.workingdir}
     * @since 1.0
     */
    private File workingDirectory;

    /**
     * Program standard and error output will be redirected to the file specified by this optional field. If not
     * specified the standard maven logging is used.
     * 
     * @parameter expression="${exec.outputFile}"
     * @since 1.1-beta-2
     */
    private File outputFile;

    /**
     * Can be of type <code>&lt;argument&gt;</code> or <code>&lt;classpath&gt;</code> Can be overriden using "exec.args"
     * env. variable
     * 
     * @parameter
     * @since 1.0
     */
    private List arguments;

    /**
     * @parameter default-value="${basedir}"
     * @required
     * @readonly
     * @since 1.0
     */
    private File basedir;

    /**
     * Environment variables to pass to the executed program.
     * 
     * @parameter
     * @since 1.1-beta-2
     */
    private Map environmentVariables = new HashMap();

    /**
     * The current build session instance. This is used for toolchain manager API calls.
     * 
     * @parameter default-value="${session}"
     * @required
     * @readonly
     */
    private MavenSession session;

    /**
     * Exit codes to be resolved as successful execution for non-compliant applications (applications not returning 0
     * for success).
     * 
     * @parameter
     * @since 1.1.1
     */
    private List successCodes;

    /**
     * If set to true the classpath and the main class will be written to a MANIFEST.MF file and wrapped into a jar.
     * Instead of '-classpath/-cp CLASSPATH mainClass' the exec plugin executes '-jar maven-exec.jar'.
     * 
     * @parameter expression="${exec.longClasspath}" default-value="false"
     * @since 1.1.2
     */
    private boolean longClasspath;

    public static final String CLASSPATH_TOKEN = "%classpath";

    /**
     * priority in the execute method will be to use System properties arguments over the pom specification.
     * 
     * @throws MojoExecutionException if a failure happens
     */
    public void execute()
        throws MojoExecutionException
    {
        try
        {
            if ( isSkip() )
            {
                getLog().info( "skipping execute as per configuraion" );
                return;
            }

            if ( basedir == null )
            {
                throw new IllegalStateException( "basedir is null. Should not be possible." );
            }

            String argsProp = getSystemProperty( "exec.args" );

            List commandArguments = new ArrayList();

            if ( hasCommandlineArgs() )
            {
                String[] args = parseCommandlineArgs();
                for ( int i = 0; i < args.length; i++ )
                {
                    if ( isLongClassPathArgument( args[i] ) )
                    {
                        // it is assumed that starting from -cp or -classpath the arguments
                        // are: -classpath/-cp %classpath mainClass
                        // the arguments are replaced with: -jar $TMP/maven-exec.jar
                        // NOTE: the jar will contain the classpath and the main class
                        commandArguments.add( "-jar" );
                        File tmpFile = createJar( computeClasspath( null ), args[i + 2] );
                        commandArguments.add( tmpFile.getAbsolutePath() );
                        i += 2;
                    }
                    else if ( CLASSPATH_TOKEN.equals( args[i] ) )
                    {
                        commandArguments.add( computeClasspathString( null ) );
                    }
                    else
                    {
                        commandArguments.add( args[i] );
                    }
                }
            }
            else if ( !isEmpty( argsProp ) )
            {
                getLog().debug( "got arguments from system properties: " + argsProp );

                try
                {
                    String[] args = CommandLineUtils.translateCommandline( argsProp );
                    commandArguments.addAll( Arrays.asList( args ) );
                }
                catch ( Exception e )
                {
                    throw new MojoExecutionException( "Couldn't parse systemproperty 'exec.args'" );
                }
            }
            else
            {
                if ( arguments != null )
                {
                    for ( int i = 0; i < arguments.size(); i++ )
                    {
                        Object argument = arguments.get( i );
                        String arg;
                        if ( argument == null )
                        {
                            throw new MojoExecutionException( "Misconfigured argument, value is null. "
                                + "Set the argument to an empty value if this is the required behaviour." );
                        }
                        else if ( argument instanceof String && isLongClassPathArgument( (String) argument ) )
                        {
                            // it is assumed that starting from -cp or -classpath the arguments
                            // are: -classpath/-cp %classpath mainClass
                            // the arguments are replaced with: -jar $TMP/maven-exec.jar
                            // NOTE: the jar will contain the classpath and the main class
                            commandArguments.add( "-jar" );
                            File tmpFile =
                                createJar( computeClasspath( (Classpath) arguments.get( i + 1 ) ),
                                           (String) arguments.get( i + 2 ) );
                            commandArguments.add( tmpFile.getAbsolutePath() );
                            i += 2;
                        }
                        else if ( argument instanceof Classpath )
                        {
                            Classpath specifiedClasspath = (Classpath) argument;

                            arg = computeClasspathString( specifiedClasspath );
                            commandArguments.add( arg );
                        }
                        else
                        {
                            arg = argument.toString();
                            commandArguments.add( arg );
                        }
                    }
                }
            }

            Map enviro = new HashMap();
            try
            {
                Properties systemEnvVars = CommandLineUtils.getSystemEnvVars();
                enviro.putAll( systemEnvVars );
            }
            catch ( IOException x )
            {
                getLog().error( "Could not assign default system enviroment variables.", x );
            }

            if ( environmentVariables != null )
            {
                Iterator iter = environmentVariables.keySet().iterator();
                while ( iter.hasNext() )
                {
                    String key = (String) iter.next();
                    String value = (String) environmentVariables.get( key );
                    enviro.put( key, value );
                }
            }

            if ( workingDirectory == null )
            {
                workingDirectory = basedir;
            }

            if ( !workingDirectory.exists() )
            {
                getLog().debug( "Making working directory '" + workingDirectory.getAbsolutePath() + "'." );
                if ( !workingDirectory.mkdirs() )
                {
                    throw new MojoExecutionException( "Could not make working directory: '"
                        + workingDirectory.getAbsolutePath() + "'" );
                }
            }

            CommandLine commandLine = getExecutablePath( enviro, workingDirectory );

            Executor exec = new DefaultExecutor();

            String[] args = new String[commandArguments.size()];
            for ( int i = 0; i < commandArguments.size(); i++ )
            {
                args[i] = (String) commandArguments.get( i );
            }

            commandLine.addArguments( args, false );

            exec.setWorkingDirectory( workingDirectory );

            // this code ensures the output gets logged vai maven logging, but at the same time prevents
            // partial line output, like input prompts.
            // final Log outputLog = getExecOutputLog();
            // LogOutputStream stdout = new LogOutputStream()
            // {
            // protected void processLine( String line, int level )
            // {
            // outputLog.info( line );
            // }
            // };
            //
            // LogOutputStream stderr = new LogOutputStream()
            // {
            // protected void processLine( String line, int level )
            // {
            // outputLog.info( line );
            // }
            // };
            OutputStream stdout = System.out;
            OutputStream stderr = System.err;

            try
            {
                getLog().debug( "Executing command line: " + commandLine );

                int resultCode = executeCommandLine( exec, commandLine, enviro, stdout, stderr );

                if ( isResultCodeAFailure( resultCode ) )
                {
                    throw new MojoExecutionException( "Result of " + commandLine + " execution is: '" + resultCode
                        + "'." );
                }
            }
            catch ( ExecuteException e )
            {
                throw new MojoExecutionException( "Command execution failed.", e );

            }
            catch ( IOException e )
            {
                throw new MojoExecutionException( "Command execution failed.", e );
            }

            registerSourceRoots();
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "I/O Error", e );
        }
    }

    boolean isResultCodeAFailure( int result )
    {
        if ( successCodes == null || successCodes.size() == 0 ) 
        {
            return result != 0;
        }
        for ( Iterator it = successCodes.iterator(); it.hasNext(); )
        {
            int code = Integer.parseInt( (String) it.next() );
            if ( code == result ) 
            {
                return false;
            }
        }
        return true;
    }

    private boolean isLongClassPathArgument( String arg )
    {
        return longClasspath && ( "-classpath".equals( arg ) || "-cp".equals( arg ) );
    }

    private Log getExecOutputLog()
    {
        Log log = getLog();
        if ( outputFile != null )
        {
            try
            {
                if ( !outputFile.getParentFile().exists() && !outputFile.getParentFile().mkdirs() )
                {
                    getLog().warn( "Could not create non existing parent directories for log file: " + outputFile );
                }
                PrintStream stream = new PrintStream( new FileOutputStream( outputFile ) );

                log = new StreamLog( stream );
            }
            catch ( Exception e )
            {
                getLog().warn( "Could not open " + outputFile + ". Using default log", e );
            }
        }

        return log;
    }

    /**
     * Compute the classpath from the specified Classpath. The computed classpath is based on the classpathScope. The
     * plugin cannot know from maven the phase it is executed in. So we have to depend on the user to tell us he wants
     * the scope in which the plugin is expected to be executed.
     * 
     * @param specifiedClasspath Non null when the user restricted the dependenceis, null otherwise (the default
     *            classpath will be used)
     * @return a platform specific String representation of the classpath
     */
    private String computeClasspathString( Classpath specifiedClasspath )
    {
        List resultList = computeClasspath( specifiedClasspath );
        StringBuffer theClasspath = new StringBuffer();

        for ( Iterator it = resultList.iterator(); it.hasNext(); )
        {
            String str = (String) it.next();
            addToClasspath( theClasspath, str );
        }

        return theClasspath.toString();
    }

    /**
     * Compute the classpath from the specified Classpath. The computed classpath is based on the classpathScope. The
     * plugin cannot know from maven the phase it is executed in. So we have to depend on the user to tell us he wants
     * the scope in which the plugin is expected to be executed.
     * 
     * @param specifiedClasspath Non null when the user restricted the dependenceis, null otherwise (the default
     *            classpath will be used)
     * @return a list of class path elements
     */
    private List computeClasspath( Classpath specifiedClasspath )
    {
        List artifacts = new ArrayList();
        List theClasspathFiles = new ArrayList();
        List resultList = new ArrayList();

        collectProjectArtifactsAndClasspath( artifacts, theClasspathFiles );

        if ( ( specifiedClasspath != null ) && ( specifiedClasspath.getDependencies() != null ) )
        {
            artifacts = filterArtifacts( artifacts, specifiedClasspath.getDependencies() );
        }

        for ( Iterator it = theClasspathFiles.iterator(); it.hasNext(); )
        {
            File f = (File) it.next();
            resultList.add( f.getAbsolutePath() );
        }

        for ( Iterator it = artifacts.iterator(); it.hasNext(); )
        {
            Artifact artifact = (Artifact) it.next();
            getLog().debug( "dealing with " + artifact );
            resultList.add( artifact.getFile().getAbsolutePath() );
        }

        return resultList;
    }

    private static void addToClasspath( StringBuffer theClasspath, String toAdd )
    {
        if ( theClasspath.length() > 0 )
        {
            theClasspath.append( File.pathSeparator );
        }
        theClasspath.append( toAdd );
    }

    private List filterArtifacts( List artifacts, Collection dependencies )
    {
        AndArtifactFilter filter = new AndArtifactFilter();

        filter.add( new IncludesArtifactFilter( new ArrayList( dependencies ) ) ); // gosh

        List filteredArtifacts = new ArrayList();
        for ( Iterator it = artifacts.iterator(); it.hasNext(); )
        {
            Artifact artifact = (Artifact) it.next();
            if ( filter.include( artifact ) )
            {
                getLog().debug( "filtering in " + artifact );
                filteredArtifacts.add( artifact );
            }
        }
        return filteredArtifacts;
    }

    CommandLine getExecutablePath( Map enviro, File dir )
    {
        File execFile = new File( executable );
        String exec = null;
        if ( execFile.exists() )
        {
            getLog().debug( "Toolchains are ignored, 'executable' parameter is set to " + executable );
            exec = execFile.getAbsolutePath();
        }
        else
        {
            Toolchain tc = getToolchain();

            // if the file doesn't exist & toolchain is null, the exec is probably in the PATH...
            // we should probably also test for isFile and canExecute, but the second one is only
            // available in SDK 6.
            if ( tc != null )
            {
                getLog().info( "Toolchain in exec-maven-plugin: " + tc );
                exec = tc.findTool( executable );
            }
            else
            {
                if ( OS.isFamilyWindows() )
                {
                    String ex = executable.indexOf( "." ) < 0 ? executable + ".bat" : executable;
                    File f = new File( dir, ex );
                    if ( f.exists() )
                    {
                        exec = ex;
                    }
                    else
                    {
                        // now try to figure the path from PATH, PATHEXT env vars
                        // if bat file, wrap in cmd /c
                        String path = (String) enviro.get( "PATH" );
                        if ( path != null )
                        {
                            String[] elems = StringUtils.split( path, File.pathSeparator );
                            for ( int i = 0; i < elems.length; i++ )
                            {
                                f = new File( new File( elems[i] ), ex );
                                if ( f.exists() )
                                {
                                    exec = ex;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        if ( exec == null )
        {
            exec = executable;
        }

        CommandLine toRet;
        if ( OS.isFamilyWindows() && exec.toLowerCase( Locale.getDefault() ).endsWith( ".bat" ) )
        {
            toRet = new CommandLine( "cmd" );
            toRet.addArgument( "/c" );
            toRet.addArgument( exec );
        }
        else
        {
            toRet = new CommandLine( exec );
        }

        return toRet;
    }

    // private String[] DEFAULT_PATH_EXT = new String[] {
    // .COM; .EXE; .BAT; .CMD; .VBS; .VBE; .JS; .JSE; .WSF; .WSH
    // ".COM", ".EXE", ".BAT", ".CMD"
    // };

    private static boolean isEmpty( String string )
    {
        return string == null || string.length() == 0;
    }

    //
    // methods used for tests purposes - allow mocking and simulate automatic setters
    //

    protected int executeCommandLine( Executor exec, CommandLine commandLine, Map enviro, OutputStream out,
                                      OutputStream err )
        throws ExecuteException, IOException
    {
        exec.setStreamHandler( new PumpStreamHandler( out, err, System.in ) );
        return exec.execute( commandLine, enviro );
    }

    void setExecutable( String executable )
    {
        this.executable = executable;
    }

    String getExecutable()
    {
        return executable;
    }

    void setWorkingDirectory( String workingDir )
    {
        setWorkingDirectory( new File( workingDir ) );
    }

    void setWorkingDirectory( File workingDir )
    {
        this.workingDirectory = workingDir;
    }

    void setArguments( List arguments )
    {
        this.arguments = arguments;
    }

    void setBasedir( File basedir )
    {
        this.basedir = basedir;
    }

    void setProject( MavenProject project )
    {
        this.project = project;
    }

    protected String getSystemProperty( String key )
    {
        return System.getProperty( key );
    }

    public void setSuccessCodes( List list )
    {
        this.successCodes = list;
    }

    public List getSuccessCodes()
    {
        return successCodes;
    }

    private Toolchain getToolchain()
    {
        Toolchain tc = null;

        try
        {
            if ( session != null ) // session is null in tests..
            {
                ToolchainManager toolchainManager =
                    (ToolchainManager) session.getContainer().lookup( ToolchainManager.ROLE );

                if ( toolchainManager != null )
                {
                    tc = toolchainManager.getToolchainFromBuildContext( "jdk", session );
                }
            }
        }
        catch ( ComponentLookupException componentLookupException )
        {
            // just ignore, could happen in pre-2.0.9 builds..
        }
        return tc;
    }

    /**
     * Create a jar with just a manifest containing a Main-Class entry for SurefireBooter and a Class-Path entry for all
     * classpath elements. Copied from surefire (ForkConfiguration#createJar())
     * 
     * @param classPath List&lt;String> of all classpath elements.
     * @return
     * @throws IOException
     */
    private File createJar( List classPath, String mainClass )
        throws IOException
    {
        File file = File.createTempFile( "maven-exec", ".jar" );
        file.deleteOnExit();
        FileOutputStream fos = new FileOutputStream( file );
        JarOutputStream jos = new JarOutputStream( fos );
        jos.setLevel( JarOutputStream.STORED );
        JarEntry je = new JarEntry( "META-INF/MANIFEST.MF" );
        jos.putNextEntry( je );

        Manifest man = new Manifest();

        // we can't use StringUtils.join here since we need to add a '/' to
        // the end of directory entries - otherwise the jvm will ignore them.
        String cp = "";
        for ( Iterator it = classPath.iterator(); it.hasNext(); )
        {
            String el = (String) it.next();
            // NOTE: if File points to a directory, this entry MUST end in '/'.
            cp += UrlUtils.getURL( new File( el ) ).toExternalForm() + " ";
        }

        man.getMainAttributes().putValue( "Manifest-Version", "1.0" );
        man.getMainAttributes().putValue( "Class-Path", cp.trim() );
        man.getMainAttributes().putValue( "Main-Class", mainClass );

        man.write( jos );
        jos.close();

        return file;
    }
}