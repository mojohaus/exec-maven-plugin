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
import org.apache.maven.plugin.AbstractMojo;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Executes the supplied java class in the current VM with the enclosing project's
 * dependencies as classpath.
 *
 * @author <a href="mailto:kaare.nilsen@gmail.com">Kaare Nilsen</a>
 * @goal java
 * @requiresDependencyResolution runtime
 * @execute phase="validate"
 */
public class ExecJavaMojo
    extends AbstractMojo
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
     * The enclosing project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

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
     * A list of system properties to be passed..
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
     * Keep the program running for n millis before terminating.
     * Note: putting 0 here has the same effect as setting keepAlive to true.
     *
     * @parameter expression="${exec.killAfter}" default-value="-1"
     */
    private long killAfter;

    /**
     * Execute goal.
     */
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        ClassLoader origClassLoader = Thread.currentThread().getContextClassLoader();

        ClassLoader myClassLoader = getClassLoader();
        Thread.currentThread().setContextClassLoader( myClassLoader );

        setSystemProperties();

        Class[] argtypes = new Class[1];
        argtypes[0] = String[].class;

        Method main;

        try
        {
            main = myClassLoader.loadClass( mainClass ).getMethod( "main", argtypes );
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Could not load main class. Terminating", e );
        }

        Object[] args = new Object[1];

        if ( null == arguments )
        {
            arguments = new String[0];
        }

        args[0] = arguments;

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

        try
        {
            main.invoke( null, args );
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Exception during program execution", e );
        }

        if ( killAfter > -1 )
        {
            waitFor( killAfter );
        }
        else if ( keepAlive )
        {
            waitFor( 0 );
        }

        Thread.currentThread().setContextClassLoader( origClassLoader );
    }

    /**
     * Pass any given system properties to the java system properties.
     */
    private void setSystemProperties()
    {
        if ( systemProperties != null )
        {
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
    private IsolatedClassLoader getClassLoader()
        throws MojoExecutionException
    {
        IsolatedClassLoader appClassloader = new IsolatedClassLoader( this.getClass().getClassLoader() );
        this.addRelevantPluginDependenciesToIsolatedClassLoader( appClassloader );
        this.addRelevantProjectDependenciesToIsolatedClassLoader( appClassloader );
        return appClassloader;
    }

    /**
     * Add any relevant project dependencies to the IsolatedClassLoader.
     * Indirectly takes includePluginDependencies and ExecutableDependency into consideration.
     *
     * @param appClassloader
     * @throws MojoExecutionException
     */
    private void addRelevantPluginDependenciesToIsolatedClassLoader( IsolatedClassLoader appClassloader )
        throws MojoExecutionException
    {
        Set dependencies = this.determineRelevantPluginDependencies();

        try
        {
            Iterator iter = dependencies.iterator();
            while ( iter.hasNext() )
            {
                Artifact classPathElement = (Artifact) iter.next();
                getLog().debug(
                    "Adding plugin dependency artifact: " + classPathElement.getArtifactId() + " to classpath" );
                appClassloader.addURL( classPathElement.getFile().toURL() );
            }
        }
        catch ( MalformedURLException e )
        {
            throw new MojoExecutionException( "Error during setting up classpath", e );
        }

    }

    /**
     * Add any relevant project dependencies to the IsolatedClassLoader.
     * Takes includeProjectDependencies into consideration.
     *
     * @param appClassloader
     * @throws MojoExecutionException
     */
    private void addRelevantProjectDependenciesToIsolatedClassLoader( IsolatedClassLoader appClassloader )
        throws MojoExecutionException
    {
        if ( this.includeProjectDependencies )
        {
            try
            {
                getLog().debug( "Project Dependencies will be included." );

                URL mainClasses = new File( project.getBuild().getOutputDirectory() ).toURL();
                getLog().debug( "Adding to classpath : " + mainClasses );
                appClassloader.addURL( mainClasses );

                URL testClasses = new File( project.getBuild().getTestOutputDirectory() ).toURL();
                getLog().debug( "Adding to classpath : " + testClasses );
                appClassloader.addURL( testClasses );

                Set dependencies = project.getArtifacts();
                Iterator iter = dependencies.iterator();
                while ( iter.hasNext() )
                {
                    Artifact classPathElement = (Artifact) iter.next();
                    getLog().debug(
                        "Adding project dependency artifact: " + classPathElement.getArtifactId() + " to classpath" );
                    appClassloader.addURL( classPathElement.getFile().toURL() );
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
            }
        }
    }

    /**
     *
     *
     */
    public class IsolatedClassLoader
        extends URLClassLoader
    {
        private ClassLoader parent;

        private Set urls = new HashSet();

        public IsolatedClassLoader()
        {
            super( new URL[0] );
            this.parent = ClassLoader.getSystemClassLoader();
        }

        public void addURL( URL url )
        {
            if ( !urls.contains( url ) )
            {
                super.addURL( url );
            }
            else
            {
                urls.add( url );
            }
        }

        public synchronized Class loadClass( String className )
            throws ClassNotFoundException
        {
            Class c = findLoadedClass( className );
            ClassNotFoundException ex = null;
            if ( c == null )
            {
                try
                {
                    c = findClass( className );
                }
                catch ( ClassNotFoundException e )
                {
                    ex = e;
                    if ( parent != null )
                    {
                        c = parent.loadClass( className );
                    }
                }
            }

            if ( c == null )
            {
                throw ex;
            }

            return c;
        }

        public IsolatedClassLoader( ClassLoader parent )
        {
            this();
            this.parent = parent;
        }
    }

}
