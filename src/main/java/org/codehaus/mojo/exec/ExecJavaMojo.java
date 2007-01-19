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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.artifact.MavenMetadataSource;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * Executes the supplied java class in the current VM with the enclosing project's
 * dependencies as classpath.
 *
 * @author <a href="mailto:kaare.nilsen@gmail.com">Kaare Nilsen</a>, <a href="mailto:dsmiley@mitre.org">David Smiley</a>
 * @goal java
 * @requiresDependencyResolution runtime
 * @execute phase="validate"
 */
public class ExecJavaMojo
    extends AbstractExecMojo
{
    /**
     * @component
     */
    private ArtifactResolver artifactResolver;

    /**
     * @component
     */
    private ArtifactFactory artifactFactory;

    /**
     * @component
     */
    private ArtifactMetadataSource metadataSource;

    /**
     * @parameter expression="${localRepository}"
     */
    private ArtifactRepository localRepository;

    /**
     * @parameter expression="${project.remoteArtifactRepositories}"
     */
    private List remoteRepositories;

    /**
     * @component
     */
    private MavenProjectBuilder projectBuilder;

    /**
     * @parameter expression="${plugin.artifacts}"
     * @readonly
     */
    private List pluginDependencies;

    /**
     * The main class to execute.
     *
     * @parameter expression="${exec.mainClass}"
     * @required
     */
    private String mainClass;

    /**
     * The class arguments.
     *
     * @parameter expression="${exec.arguments}"
     */
    private String[] arguments;

    /**
     * A list of system properties to be passed. Note: as the execution is not forked, some system properties
     * required by the JVM cannot be passed here. Use MAVEN_OPTS or the exec:exec instead. See the user guide for more information.
     *
     * @parameter
     */
    private Property[] systemProperties;

    /**
     * Indicates if mojo should be kept running after the mainclass terminates.
     * Usefull for serverlike apps with deamonthreads.
     *
     * @parameter expression="${exec.keepAlive}" default-value="false"
     */
    private boolean keepAlive;

    /**
     * Indicates if the project dependencies should be used when executing
     * the main class.
     *
     * @parameter expression="${exec.includeProjectDependencies}" default-value="true"
     */
    private boolean includeProjectDependencies;

    /**
     * Indicates if this plugin's dependencies should be used when executing
     * the main class.
     * <p/>
     * This is useful when project dependencies are not appropriate.  Using only
     * the plugin dependencies can be particularly useful when the project is
     * not a java project.  For example a mvn project using the csharp plugins
     * only expects to see dotnet libraries as dependencies.
     *
     * @parameter expression="${exec.includePluginDependencies}" default-value="false"
     */
    private boolean includePluginDependencies;

    /**
     * If provided the ExecutableDependency identifies which of the plugin dependencies
     * contains the executable class.  This will have the affect of only including
     * plugin dependencies required by the identified ExecutableDependency.
     * <p/>
     * If includeProjectDependencies is set to true, all of the project dependencies
     * will be included on the executable's classpath.  Whether a particular project
     * dependency is a dependency of the identified ExecutableDependency will be
     * irrelevant to its inclusion in the classpath.
     *
     * @parameter
     * @optional
     */
    private ExecutableDependency executableDependency;

    /**
     * Deprecated this is not needed anymore.
     *
     * @parameter expression="${exec.killAfter}" default-value="-1"
     */
    private long killAfter;
    private Properties originalSystemProperties;

    /**
     * Execute goal.
     */
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( killAfter != -1 )
        {
            getLog().warn( "Warning: killAfter is now deprecated. Do you need it ? Please comment on MEXEC-6." );
        }

        if ( null == arguments )
        {
            arguments = new String[0];
        }

        if ( getLog().isDebugEnabled() )
        {
            StringBuffer msg = new StringBuffer( "Invoking : " );
            msg.append( mainClass );
            msg.append( ".main(" );
            for ( int i = 0; i < arguments.length; i++ )
            {
                if ( i > 0 )
                {
                    msg.append( ", " );
                }
                msg.append( arguments[i] );
            }
            msg.append( ")" );
            getLog().debug(  msg );
        }

        IsolatedThreadGroup threadGroup = new IsolatedThreadGroup( mainClass /*name*/ );
        Thread bootstrapThread = new Thread( threadGroup, new Runnable()
        {
            public void run()
            {
                try
                {
                    Method main = Thread.currentThread().getContextClassLoader().loadClass( mainClass )
                        .getMethod( "main", new Class[]{String[].class} );
                    if ( ! main.isAccessible() )
                    {
                        getLog().debug( "Setting accessibility to true in order to invoke main()." );
                        main.setAccessible(true);
                    }
                    main.invoke( main, new Object[]{arguments} );
                }
                catch ( Exception e )
                {   // just pass it on
                    Thread.currentThread().getThreadGroup().uncaughtException( Thread.currentThread(), e );
                }
            }
        }, mainClass /*name*/ );
        bootstrapThread.setContextClassLoader( getClassLoader() );
        setSystemProperties();

        bootstrapThread.start();
        joinNonDaemonThreads( threadGroup );

        if ( keepAlive )
        {
            waitFor( 0 );
        }

        terminateThreads( threadGroup );
        try
        {
            threadGroup.destroy();
        }
        catch (IllegalThreadStateException e)
        {
            getLog().warn( "Couldn't destroy threadgroup " + threadGroup, e );
        }

        if ( originalSystemProperties != null )
        {
            System.setProperties( originalSystemProperties );
        }

        synchronized (threadGroup)
        {
            if ( threadGroup.uncaughtException != null )
            {
                throw new MojoExecutionException( null, threadGroup.uncaughtException );
            }
        }

        registerSourceRoots();
    }

    class IsolatedThreadGroup extends ThreadGroup
    {
        Throwable uncaughtException; //synchronize access to this

        public IsolatedThreadGroup( String name )
        {
            super( name );
        }

        public void uncaughtException( Thread thread, Throwable throwable )
        {
            if ( throwable instanceof ThreadDeath )
            {
                return; //harmless
            }
            boolean doLog = false;
            synchronized ( this )
            {
                if ( uncaughtException == null ) // only remember the first one
                {
                    uncaughtException = throwable; // will be reported eventually
                }
                else
                {
                    doLog = true;
                }
            }
            if ( doLog )
            {
                getLog().warn( "an additional exception was thrown", throwable );
            }
        }
    }

    private void joinNonDaemonThreads( ThreadGroup threadGroup )
    {
        Thread[] threads = new Thread[ threadGroup.activeCount() ]; //activeCount includes subgroups
        threadGroup.enumerate( threads ); //enumerate includes subgroups
        boolean foundNonDaemon = false;
        for ( int i = 0; i < threads.length; i++ )
        {
            Thread thread = threads[i];
            if ( thread == null )
            {
                break;
            }
            if ( thread.isDaemon() )
            {
                continue;
            }
            foundNonDaemon = true;
            joinThread( thread );
        }

        //try again; maybe more threads were created while we were busy
        if ( foundNonDaemon )
        {
            joinNonDaemonThreads( threadGroup );
        }
    }

    private void joinThread( Thread thread )
    {
        try
        {
           getLog().debug( "joining on thread " + thread );
           thread.join();
        }
        catch ( InterruptedException e )
        {
           getLog().warn( "interrupted while joining against thread " + thread, e );
        }
    }

    private void terminateThreads( ThreadGroup threadGroup )
    {
        // this gets one active thread at a time. New threads might be created at
        // any time, so this logic is good.
        Thread[] thread = new Thread[1];
        while ( true )
        {
            if ( threadGroup.enumerate( thread ) == 0 ) //places at most one active thread into the list
            {
                break;
            }
            getLog().debug( "interrupting thread " + thread[0] );
            thread[0].interrupt();
            joinThread( thread[0] );
        }

        int activeCount = threadGroup.activeCount();
        if ( activeCount != 0 )
        {
            //TODO this may be nothing; continue on anyway; perhaps don't even log in future
            threadGroup.enumerate(thread);
            getLog().debug( "strange; " + activeCount
                    + " thread(s) still active in the group " + threadGroup +" such as " + thread[0] );
        }
    }

    /**
     * Pass any given system properties to the java system properties.
     */
    private void setSystemProperties()
    {
        if ( systemProperties != null )
        {
            originalSystemProperties = System.getProperties();
            for ( int i = 0; i < systemProperties.length; i++ )
            {
                Property systemProperty = systemProperties[i];
                String value = systemProperty.getValue();
                System.setProperty( systemProperty.getKey(), value == null ? "" : value );
            }
        }
    }

    /**
     * Set up a classloader for the execution of the
     * main class.
     *
     * @return
     * @throws MojoExecutionException
     */
    private ClassLoader getClassLoader()
        throws MojoExecutionException
    {
        List classpathURLs = new ArrayList();
        this.addRelevantPluginDependenciesToClasspath( classpathURLs );
        this.addRelevantProjectDependenciesToClasspath( classpathURLs );
        return new URLClassLoader((URL[]) classpathURLs.toArray( new URL[ classpathURLs.size() ] )/*,
                ClassLoader.getSystemClassLoader()*/);
    }

    /**
     * Add any relevant project dependencies to the classpath.
     * Indirectly takes includePluginDependencies and ExecutableDependency into consideration.
     *
     * @param path classpath of {@link java.net.URL} objects
     * @throws MojoExecutionException
     */
    private void addRelevantPluginDependenciesToClasspath( List path )
        throws MojoExecutionException
    {
        if ( hasCommandlineArgs() )
        {
            arguments = parseCommandlineArgs();
        }

        try
        {
            Iterator iter = this.determineRelevantPluginDependencies().iterator();
            while ( iter.hasNext() )
            {
                Artifact classPathElement = (Artifact) iter.next();
                getLog().debug(
                    "Adding plugin dependency artifact: " + classPathElement.getArtifactId() + " to classpath" );
                path.add( classPathElement.getFile().toURL() );
            }
        }
        catch ( MalformedURLException e )
        {
            throw new MojoExecutionException( "Error during setting up classpath", e );
        }

    }

    /**
     * Add any relevant project dependencies to the classpath.
     * Takes includeProjectDependencies into consideration.
     *
     * @param path classpath of {@link java.net.URL} objects
     * @throws MojoExecutionException
     */
    private void addRelevantProjectDependenciesToClasspath( List path )
        throws MojoExecutionException
    {
        if ( this.includeProjectDependencies )
        {
            try
            {
                getLog().debug( "Project Dependencies will be included." );

                URL mainClasses = new File( project.getBuild().getOutputDirectory() ).toURL();
                getLog().debug( "Adding to classpath : " + mainClasses );
                path.add( mainClasses );

                URL testClasses = new File( project.getBuild().getTestOutputDirectory() ).toURL();
                getLog().debug( "Adding to classpath : " + testClasses );
                path.add( testClasses );

                Set dependencies = project.getArtifacts();
                Iterator iter = dependencies.iterator();
                while ( iter.hasNext() )
                {
                    Artifact classPathElement = (Artifact) iter.next();
                    getLog().debug(
                        "Adding project dependency artifact: " + classPathElement.getArtifactId() + " to classpath" );
                    path.add( classPathElement.getFile().toURL() );
                }
            }
            catch ( MalformedURLException e )
            {
                throw new MojoExecutionException( "Error during setting up classpath", e );
            }
        }
        else
        {
            getLog().debug( "Project Dependencies will be excluded." );
        }

    }

    /**
     * Determine all plugin dependencies relevant to the executable.
     * Takes includePlugins, and the executableDependency into consideration.
     *
     * @return a set of Artifact objects.
     *         (Empty set is returned if there are no relevant plugin dependencies.)
     * @throws MojoExecutionException
     */
    private Set determineRelevantPluginDependencies()
        throws MojoExecutionException
    {
        Set relevantDependencies;
        if ( this.includePluginDependencies )
        {
            if ( this.executableDependency == null )
            {
                getLog().debug( "All Plugin Dependencies will be included." );
                relevantDependencies = new HashSet( this.pluginDependencies );
            }
            else
            {
                getLog().debug( "Selected plugin Dependencies will be included." );
                Artifact executableArtifact = this.findExecutableArtifact();
                Artifact executablePomArtifact = this.getExecutablePomArtifact( executableArtifact );
                relevantDependencies = this.resolveExecutableDependencies( executablePomArtifact );
            }
        }
        else
        {
            relevantDependencies = Collections.EMPTY_SET;
            getLog().debug( "Plugin Dependencies will be excluded." );
        }
        return relevantDependencies;
    }

    /**
     * Get the artifact which refers to the POM of the executable artifact.
     *
     * @param executableArtifact this artifact refers to the actual assembly.
     * @return an artifact which refers to the POM of the executable artifact.
     */
    private Artifact getExecutablePomArtifact( Artifact executableArtifact )
    {
        return this.artifactFactory.createBuildArtifact( executableArtifact.getGroupId(),
                                                         executableArtifact.getArtifactId(),
                                                         executableArtifact.getVersion(), "pom" );
    }

    /**
     * Examine the plugin dependencies to find the executable artifact.
     *
     * @return an artifact which refers to the actual executable tool (not a POM)
     * @throws MojoExecutionException
     */
    private Artifact findExecutableArtifact()
        throws MojoExecutionException
    {
        //ILimitedArtifactIdentifier execToolAssembly = this.getExecutableToolAssembly();

        Artifact executableTool = null;
        for ( Iterator iter = this.pluginDependencies.iterator(); iter.hasNext(); )
        {
            Artifact pluginDep = (Artifact) iter.next();
            if ( this.executableDependency.matches( pluginDep ) )
            {
                executableTool = pluginDep;
                break;
            }
        }

        if ( executableTool == null )
        {
            throw new MojoExecutionException(
                "No dependency of the plugin matches the specified executableDependency." +
                    "  Specified executableToolAssembly is: " + executableDependency.toString() );
        }

        return executableTool;
    }

    private Set resolveExecutableDependencies( Artifact executablePomArtifact )
        throws MojoExecutionException
    {

        Set executableDependencies;
        try
        {
            MavenProject executableProject = this.projectBuilder.buildFromRepository( executablePomArtifact,
                                                                                      this.remoteRepositories,
                                                                                      this.localRepository );

            //get all of the dependencies for the executable project
            List dependencies = executableProject.getDependencies();

            //make Artifacts of all the dependencies
            Set dependencyArtifacts =
                MavenMetadataSource.createArtifacts( this.artifactFactory, dependencies, null, null, null );

            //not forgetting the Artifact of the project itself
            dependencyArtifacts.add( executableProject.getArtifact() );

            //resolve all dependencies transitively to obtain a comprehensive list of assemblies
            ArtifactResolutionResult result = artifactResolver.resolveTransitively( dependencyArtifacts,
                                                                                    executablePomArtifact,
                                                                                    Collections.EMPTY_MAP,
                                                                                    this.localRepository,
                                                                                    this.remoteRepositories,
                                                                                    metadataSource, null,
                                                                                    Collections.EMPTY_LIST );
            executableDependencies = result.getArtifacts();

        }
        catch ( Exception ex )
        {
            throw new MojoExecutionException(
                "Encountered problems resolving dependencies of the executable " + "in preparation for its execution.",
                ex );
        }

        return executableDependencies;
    }

    /**
     * Stop program execution for nn millis.
     *
     * @param millis the number of millis-seconds to wait for,
     *               <code>0</code> stops program forever.
     */
    private void waitFor( long millis )
    {
        Object lock = new Object();
        synchronized ( lock )
        {
            try
            {
                lock.wait( millis );
            }
            catch ( InterruptedException e )
            {
                getLog().warn( "Spuriously interrupted while waiting for " + millis + "ms", e);
            }
        }
    }

}
