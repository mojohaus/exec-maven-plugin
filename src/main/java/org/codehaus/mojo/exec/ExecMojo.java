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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
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
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineUtils;

/**
 * A Plugin for executing external programs.
 * 
 * @author Jerome Lacoste <jerome@coffeebreaks.org>
 * @version $Id$
 * @goal exec
 * @requiresDependencyResolution test
 * @threadSafe
 * @since 1.0
 */
public class ExecMojo
    extends AbstractExecMojo
{
    /**
     * <p>
     * The executable. Can be a full path or the name of the executable. In the latter case, the executable must be in
     * the PATH for the execution to work.
     * </p>
     * <p>
     * The plugin will search for the executable in the following order:
     * <ol>
     * <li>relative to the root of the project</li>
     * <li>as toolchain executable</li>
     * <li>relative to the working directory (Windows only)</li>
     * <li>relative to the directories specified in the system property PATH (Windows Only)</li>
     * </ol>
     * Otherwise use the executable as is.
     * </p>
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
     * specified the standard Maven logging is used. <br/>
     * <strong>Note:</strong> Be aware that <code>System.out</code> and <code>System.err</code> use buffering, so don't
     * rely on the order!
     * 
     * @parameter expression="${exec.outputFile}"
     * @since 1.1-beta-2
     * @see java.lang.System#err
     * @see java.lang.System#in
     */
    private File outputFile;

    /**
     * <p>
     * A list of arguments passed to the {@code executable}, which should be of type <code>&lt;argument&gt;</code> or
     * <code>&lt;classpath&gt;</code>. Can be overridden by using the <code>exec.args</code> environment variable.
     * </p>
     * 
     * @parameter
     * @since 1.0
     */
    private List<?> arguments;

    /**
     * <p>
     * This will control if you like to fail the build if an arguments element is empty. This means that for arguments
     * which would evaluate to <code>Null</code> an <code>""</code> empty string will be added to the command line of
     * the {@code executable} which will be called.
     * </p>
     * 
     * @parameter default-value="true"
     * @since 1.3
     * @see #arguments
     */
    private boolean failWithEmptyArgument;

    /**
     * <p>
     * The following will control if you like to get a warning during the build if an entry either key/value of
     * environmentVariables will be evaluated to <code>Null</code> an <code>""</code> empty string will be used instead.
     * </p>
     * 
     * @parameter default-value="true"
     * @since 1.3
     * @see #environmentVariables
     */
    private boolean failWithNullKeyOrValueInEnvironment;

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
    private Map<String, String> environmentVariables = new HashMap<String, String>();

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
    private int[] successCodes;

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
        if ( isSkip() )
        {
            getLog().info( "skipping execute as per configuraion" );
            return;
        }

        if ( basedir == null )
        {
            throw new IllegalStateException( "basedir is null. Should not be possible." );
        }

        try
        {

            handleWorkingDirectory();

            String argsProp = getSystemProperty( "exec.args" );

            List<String> commandArguments = new ArrayList<String>();

            if ( hasCommandlineArgs() )
            {
                handleCommandLineArgs( commandArguments );
            }
            else if ( !StringUtils.isEmpty( argsProp ) )
            {
                handleSystemPropertyArguments( argsProp, commandArguments );
            }
            else
            {
                if ( arguments != null )
                {
                    handleArguments( commandArguments );
                }
            }

            Map<String, String> enviro = handleSystemEnvVariables();

            CommandLine commandLine = getExecutablePath( enviro, workingDirectory );

            String[] args = commandArguments.toArray( new String[commandArguments.size()] );

            commandLine.addArguments( args, false );

            Executor exec = new DefaultExecutor();
            exec.setWorkingDirectory( workingDirectory );
            fillSuccessCodes( exec );

            getLog().debug( "Executing command line: " + commandLine );

            try
            {
                int resultCode;
                if ( outputFile != null )
                {
                    if ( !outputFile.getParentFile().exists() && !outputFile.getParentFile().mkdirs() )
                    {
                        getLog().warn( "Could not create non existing parent directories for log file: " + outputFile );
                    }

                    FileOutputStream outputStream = null;
                    try
                    {
                        outputStream = new FileOutputStream( outputFile );

                        resultCode = executeCommandLine( exec, commandLine, enviro, outputStream );
                    }
                    finally
                    {
                        IOUtil.close( outputStream );
                    }
                }
                else
                {
                    resultCode = executeCommandLine( exec, commandLine, enviro, System.out, System.err );
                }

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

    private Map<String, String> handleSystemEnvVariables() throws MojoExecutionException
    {
        Map<String, String> enviro = new HashMap<String, String>();
        try
        {
            Properties systemEnvVars = CommandLineUtils.getSystemEnvVars();
            for ( Map.Entry<?, ?> entry : systemEnvVars.entrySet() )
            {
                enviro.put( (String) entry.getKey(), (String) entry.getValue() );
            }
        }
        catch ( IOException x )
        {
            getLog().error( "Could not assign default system enviroment variables.", x );
        }

        if ( environmentVariables != null )
        {
            for ( Map.Entry<String, String> item : environmentVariables.entrySet() )
            {
                getLog().debug( "Entry: key:" + item.getKey() + " value:" + item.getValue() );

                // The following checks are only in relationship with MEXEC-108
                // (https://issues.apache.org/jira/browse/EXEC-80)
                if ( item.getKey() == null )
                {
                    if ( failWithNullKeyOrValueInEnvironment )
                    {
                        throw new MojoExecutionException(
                                                         "The defined environment contains an entry with null key. This could cause failures." );
                    }
                    else
                    {
                        getLog().warn( "The defined environment contains an entry with null key. This could cause failures." );
                    }
                }
                if ( item.getValue() == null )
                {
                    if ( failWithNullKeyOrValueInEnvironment )
                    {
                        throw new MojoExecutionException(
                                                         "The defined environment contains an entry with null value (key:"
                                                             + item.getKey() + "). This could cause failures." );
                    }
                    else
                    {
                        getLog().warn( "The defined environment contains an entry with null value (key:" + item.getKey()
                                       + "). This could cause failures." );
                    }
                }
            }
            enviro.putAll( environmentVariables );
        }
        return enviro;
    }

    /**
     * This method is a convenient method to make the execute method a little bit more readable. It will define the
     * workingDirectory to be the baseDir in case of workingDirectory is null. If the workingDirectory does not exist it
     * will created.
     * 
     * @throws MojoExecutionException
     */
    private void handleWorkingDirectory()
        throws MojoExecutionException
    {
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
    }

    private void handleSystemPropertyArguments( String argsProp, List<String> commandArguments )
        throws MojoExecutionException
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

    private void handleCommandLineArgs( List<String> commandArguments )
        throws MojoExecutionException, IOException
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
            else if ( args[i].contains( CLASSPATH_TOKEN ) )
            {
                commandArguments.add( args[i].replace( CLASSPATH_TOKEN, computeClasspathString( null ) ) );
            }
            else
            {
                commandArguments.add( args[i] );
            }
        }
    }

    private void handleArguments( List<String> commandArguments )
        throws MojoExecutionException, IOException
    {
        for ( int i = 0; i < arguments.size(); i++ )
        {
            Object argument = arguments.get( i );
            String arg;
            if ( argument == null )
            {
                if ( failWithEmptyArgument )
                {
                    throw new MojoExecutionException( "Misconfigured argument (" + ( i + 1 ) + "), value is null. "
                        + "Set the argument to an empty value if this is the required behaviour." );

                }
                else
                {
                    // Just add an empty string to the argument list.
                    commandArguments.add( "" );
                }
            }
            else if ( argument instanceof String && isLongClassPathArgument( (String) argument ) )
            {
                // it is assumed that starting from -cp or -classpath the arguments
                // are: -classpath/-cp %classpath mainClass
                // the arguments are replaced with: -jar $TMP/maven-exec.jar
                // NOTE: the jar will contain the classpath and the main class
                commandArguments.add( "-jar" );
                File tmpFile =
                    createJar( computeClasspath( (Classpath) arguments.get( i + 1 ) ), (String) arguments.get( i + 2 ) );
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

    private void fillSuccessCodes( Executor exec )
    {
        if ( successCodes != null && successCodes.length > 0 )
        {
            exec.setExitValues( successCodes );
        }
    }

    boolean isResultCodeAFailure( int result )
    {
        if ( successCodes == null || successCodes.length == 0 )
        {
            return result != 0;
        }
        for ( int successCode : successCodes )
        {
            if ( successCode == result )
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

    /**
     * Compute the classpath from the specified Classpath. The computed classpath is based on the classpathScope. The
     * plugin cannot know from maven the phase it is executed in. So we have to depend on the user to tell us he wants
     * the scope in which the plugin is expected to be executed.
     * 
     * @param specifiedClasspath Non null when the user restricted the dependencies, <code>null</code> otherwise (the
     *            default classpath will be used)
     * @return a platform specific String representation of the classpath
     */
    private String computeClasspathString( Classpath specifiedClasspath )
    {
        List<String> resultList = computeClasspath( specifiedClasspath );
        StringBuffer theClasspath = new StringBuffer();

        for ( String str : resultList )
        {
            addToClasspath( theClasspath, str );
        }

        return theClasspath.toString();
    }

    /**
     * Compute the classpath from the specified Classpath. The computed classpath is based on the classpathScope. The
     * plugin cannot know from maven the phase it is executed in. So we have to depend on the user to tell us he wants
     * the scope in which the plugin is expected to be executed.
     * 
     * @param specifiedClasspath Non null when the user restricted the dependencies, <code>null</code> otherwise (the
     *            default classpath will be used)
     * @return a list of class path elements
     */
    private List<String> computeClasspath( Classpath specifiedClasspath )
    {
        List<Artifact> artifacts = new ArrayList<Artifact>();
        List<File> theClasspathFiles = new ArrayList<File>();
        List<String> resultList = new ArrayList<String>();

        collectProjectArtifactsAndClasspath( artifacts, theClasspathFiles );

        if ( ( specifiedClasspath != null ) && ( specifiedClasspath.getDependencies() != null ) )
        {
            artifacts = filterArtifacts( artifacts, specifiedClasspath.getDependencies() );
        }

        for ( File f : theClasspathFiles )
        {
            resultList.add( f.getAbsolutePath() );
        }

        for ( Artifact artifact : artifacts )
        {
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

    private List<Artifact> filterArtifacts( List<Artifact> artifacts, Collection<String> dependencies )
    {
        AndArtifactFilter filter = new AndArtifactFilter();

        filter.add( new IncludesArtifactFilter( new ArrayList<String>( dependencies ) ) ); // gosh

        List<Artifact> filteredArtifacts = new ArrayList<Artifact>();
        for ( Artifact artifact : artifacts )
        {
            if ( filter.include( artifact ) )
            {
                getLog().debug( "filtering in " + artifact );
                filteredArtifacts.add( artifact );
            }
        }
        return filteredArtifacts;
    }

    CommandLine getExecutablePath( Map<String, String> enviro, File dir )
    {
        File execFile = new File( executable );
        String exec = null;
        if ( execFile.isFile() )
        {
            getLog().debug( "Toolchains are ignored, 'executable' parameter is set to " + executable );
            exec = execFile.getAbsolutePath();
        }

        if ( exec == null )
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
                    String ex = !executable.contains( "." ) ? executable + ".bat" : executable;
                    File f = new File( dir, ex );
                    if ( f.isFile() )
                    {
                        exec = ex;
                    }

                    if ( exec == null )
                    {
                        // now try to figure the path from PATH, PATHEXT env vars
                        // if bat file, wrap in cmd /c
                        String path = enviro.get( "PATH" );
                        if ( path != null )
                        {
                            String[] elems = StringUtils.split( path, File.pathSeparator );
                            for ( String elem : elems )
                            {
                                f = new File( new File( elem ), ex );
                                if ( f.isFile() )
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

    //
    // methods used for tests purposes - allow mocking and simulate automatic setters
    //

    protected int executeCommandLine( Executor exec, CommandLine commandLine, Map<String, String> enviro,
                                      OutputStream out, OutputStream err )
        throws ExecuteException, IOException
    {
        BufferedOutputStream bosStdOut = new BufferedOutputStream( out );
        BufferedOutputStream bosStdErr = new BufferedOutputStream( err );
        PumpStreamHandler psh = new PumpStreamHandler( bosStdOut, bosStdErr, System.in );
        exec.setStreamHandler( psh );

        int result;
        try
        {
            psh.start();
            result = exec.execute( commandLine, enviro );
        }
        finally
        {
            psh.stop();
            bosStdOut.close();
            bosStdErr.close();
            out.close();
            err.close();
        }
        return result;
    }

    protected int executeCommandLine( Executor exec, CommandLine commandLine, Map<String, String> enviro,
                                      FileOutputStream outputFile )
        throws ExecuteException, IOException
    {
        BufferedOutputStream bos = new BufferedOutputStream( outputFile );
        PumpStreamHandler psh = new PumpStreamHandler( bos );
        exec.setStreamHandler( psh );

        int result;
        try
        {
            psh.start();
            result = exec.execute( commandLine, enviro );
        }
        finally
        {
            psh.stop();
            bos.close();
            outputFile.close();
        }
        return result;
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

    void setArguments( List<?> arguments )
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

    public void setSuccessCodes( Integer... list )
    {
        this.successCodes = new int[list.length];
        for ( int index = 0; index < list.length; index++ )
        {
            successCodes[index] = list[index];
        }
    }

    public int[] getSuccessCodes()
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
    private File createJar( List<String> classPath, String mainClass )
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
        for ( String el : classPath )
        {
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