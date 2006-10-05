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
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.Iterator;
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
     * The enclosing project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

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
            args[0] = new String[0];
        }
        else
        {
            args[0] = arguments;
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
                System.setProperty( systemProperty.getKey(), systemProperty.getValue() );
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
        try
        {
            IsolatedClassLoader appClassloader = new IsolatedClassLoader( this.getClass().getClassLoader() );

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
                getLog().debug( "Adding artifact: " + classPathElement.getArtifactId() + " to classpath" );
                appClassloader.addURL( classPathElement.getFile().toURL() );
            }
            return appClassloader;
        }
        catch ( MalformedURLException e )
        {
            throw new MojoExecutionException( "Error during setting up classpath", e );
        }
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
