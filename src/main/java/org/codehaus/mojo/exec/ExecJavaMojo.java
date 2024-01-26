package org.codehaus.mojo.exec;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.util.filter.DependencyFilterUtils;

import static java.util.stream.Collectors.toList;

/**
 * Executes the supplied java class in the current VM with the enclosing project's dependencies as classpath.
 *
 * @author Kaare Nilsen (kaare.nilsen@gmail.com), David Smiley (dsmiley@mitre.org)
 * @since 1.0
 */
@Mojo(name = "java", threadSafe = true, requiresDependencyResolution = ResolutionScope.TEST)
public class ExecJavaMojo extends AbstractExecMojo {
    // Implementation note: Constants can be included in javadocs by {@value #MY_CONST}
    private static final String THREAD_STOP_UNAVAILABLE =
            "Thread.stop() is unavailable in this JRE version, cannot force-stop any threads";

    @Component
    private RepositorySystem repositorySystem;

    /**
     * The main class to execute.<br>
     * With Java 9 and above you can prefix it with the modulename, e.g. <code>com.greetings/com.greetings.Main</code>
     * Without modulename the classpath will be used, with modulename a new modulelayer will be created.
     * <p>
     * Note that you can also provide a {@link Runnable} fully qualified name.
     * The runnable can get constructor injections either by type if you have maven in your classpath (can be provided)
     * or by name (ensure to enable {@code -parameters} Java compiler option) for loose coupling.
     * Current support loose injections are:
     * <ul>
     *     <li><code>systemProperties</code>: <code>Properties</code>, session system properties</li>
     *     <li><code>systemPropertiesUpdater</code>: <code>BiConsumer&lt;String, String&gt;</code>, session system properties update callback (pass the key/value to update, null value means removal of the key)</li>
     *     <li><code>userProperties</code>: <code>Properties</code>, session user properties</li>
     *     <li><code>userPropertiesUpdater</code>: <code>BiConsumer&lt;String, String&gt;</code>, session user properties update callback (pass the key/value to update, null value means removal of the key)</li>
     *     <li><code>projectProperties</code>: <code>Properties</code>, project properties</li>
     *     <li><code>projectPropertiesUpdater</code>: <code>BiConsumer&lt;String, String&gt;</code>, project properties update callback (pass the key/value to update, null value means removal of the key)</li>
     *     <li><code>highestVersionResolver</code>: <code>Function&lt;String, String&gt;</code>, passing a <code>groupId:artifactId</code> you get the latest resolved version from the project repositories</li>
     * </ul>
     *
     * @since 1.0
     */
    @Parameter(required = true, property = "exec.mainClass")
    private String mainClass;

    /**
     * Forces the creation of fork join common pool to avoids the threads to be owned by the isolated thread group
     * and prevent a proper shutdown.
     * If set to zero the default parallelism is used to precreate all threads,
     * if negative it is ignored else the value is the one used to create the fork join threads.
     *
     * @since 3.0.1
     */
    @Parameter(property = "exec.preloadCommonPool", defaultValue = "0")
    private int preloadCommonPool;

    /**
     * The class arguments.
     *
     * @since 1.0
     */
    @Parameter(property = "exec.arguments")
    private String[] arguments;

    /**
     * A list of system properties to be passed. Note: as the execution is not forked, some system properties required
     * by the JVM cannot be passed here. Use MAVEN_OPTS or the exec:exec instead. See the user guide for more
     * information.
     *
     * @since 1.0
     */
    @Parameter
    private AbstractProperty[] systemProperties;

    /**
     * Indicates if mojo should be kept running after the mainclass terminates. Use full for server like apps with
     * daemon threads.
     *
     * @deprecated since 1.1-alpha-1
     * @since 1.0
     */
    @Parameter(property = "exec.keepAlive", defaultValue = "false")
    @Deprecated
    private boolean keepAlive;

    /**
     * Indicates if the project dependencies should be used when executing the main class.
     *
     * @since 1.1-beta-1
     */
    @Parameter(property = "exec.includeProjectDependencies", defaultValue = "true")
    private boolean includeProjectDependencies;

    /**
     * Indicates if this plugin's dependencies should be used when executing the main class.
     * <p/>
     * This is useful when project dependencies are not appropriate. Using only the plugin dependencies can be
     * particularly useful when the project is not a java project. For example a mvn project using the csharp plugins
     * only expects to see dotnet libraries as dependencies.
     *
     * @since 1.1-beta-1
     */
    @Parameter(property = "exec.includePluginsDependencies", defaultValue = "false")
    private boolean includePluginDependencies;

    /**
     * Whether to interrupt/join and possibly stop the daemon threads upon quitting. <br/>
     * If this is <code>false</code>, maven does nothing about the daemon threads. When maven has no more work to do,
     * the VM will normally terminate any remaining daemon threads.
     * <p>
     * In certain cases (in particular if maven is embedded), you might need to keep this enabled to make sure threads
     * are properly cleaned up to ensure they don't interfere with subsequent activity. In that case, see
     * {@link #daemonThreadJoinTimeout} and {@link #stopUnresponsiveDaemonThreads} for further tuning.
     * </p>
     *
     * @since 1.1-beta-1
     */
    @Parameter(property = "exec.cleanupDaemonThreads", defaultValue = "true")
    private boolean cleanupDaemonThreads;

    /**
     * This defines the number of milliseconds to wait for daemon threads to quit following their interruption.<br/>
     * This is only taken into account if {@link #cleanupDaemonThreads} is <code>true</code>. A value &lt;=0 means to
     * not timeout (i.e. wait indefinitely for threads to finish). Following a timeout, a warning will be logged.
     * <p>
     * Note: properly coded threads <i>should</i> terminate upon interruption but some threads may prove problematic: as
     * the VM does interrupt daemon threads, some code may not have been written to handle interruption properly. For
     * example java.util.Timer is known to not handle interruptions in JDK &lt;= 1.6. So it is not possible for us to
     * infinitely wait by default otherwise maven could hang. A sensible default value has been chosen, but this default
     * value <i>may change</i> in the future based on user feedback.
     * </p>
     *
     * @since 1.1-beta-1
     */
    @Parameter(property = "exec.daemonThreadJoinTimeout", defaultValue = "15000")
    private long daemonThreadJoinTimeout;

    /**
     * Wether to call {@link Thread#stop()} following a timing out of waiting for an interrupted thread to finish. This
     * is only taken into account if {@link #cleanupDaemonThreads} is <code>true</code> and the
     * {@link #daemonThreadJoinTimeout} threshold has been reached for an uncooperative thread. If this is
     * <code>false</code>, or if {@link Thread#stop()} fails to get the thread to stop, then a warning is logged and
     * Maven will continue on while the affected threads (and related objects in memory) linger on. Consider setting
     * this to <code>true</code> if you are invoking problematic code that you can't fix. An example is
     * {@link java.util.Timer} which doesn't respond to interruption. To have <code>Timer</code> fixed, vote for
     * <a href="https://bugs.java.com/bugdatabase/view_bug?bug_id=6336543">this bug</a>.
     * <p>
     * <b>Note:</b> In JDK 20+, the long deprecated {@link Thread#stop()} (since JDK 1.2) has been removed and will
     * throw an {@link UnsupportedOperationException}. This will be handled gracefully, yielding a log warning
     * {@value #THREAD_STOP_UNAVAILABLE} once and not trying to stop any further threads during the same execution.
     *
     * @since 1.1-beta-1
     */
    @Parameter(property = "exec.stopUnresponsiveDaemonThreads", defaultValue = "false")
    private boolean stopUnresponsiveDaemonThreads;

    private Properties originalSystemProperties;

    /**
     * Additional elements to be appended to the classpath.
     *
     * @since 1.3
     */
    @Parameter
    private List<String> additionalClasspathElements;

    /**
     * List of file to exclude from the classpath.
     * It matches the jar name, for example {@code slf4j-simple-1.7.30.jar}.
     *
     * @since 3.0.1
     */
    @Parameter
    private List<String> classpathFilenameExclusions;

    /**
     * Whether to try and prohibit the called Java program from terminating the JVM (and with it the whole Maven build)
     * by calling {@link System#exit(int)}. When active, loaded classes will replace this call by a custom callback.
     * In case of an exit code 0 (OK), it will simply log the fact that {@link System#exit(int)} was called.
     * Otherwise, it will throw a {@link SystemExitException}, failing the Maven goal as if the called Java code itself
     * had exited with an exception.
     * This way, the error is propagated without terminating the whole Maven JVM. In previous versions, users
     * had to use the {@code exec} instead of the {@code java} goal in such cases, which now with this option is no
     * longer necessary.
     *
     * @since 3.2.0
     */
    @Parameter(property = "exec.blockSystemExit", defaultValue = "false")
    private boolean blockSystemExit;

    @Component // todo: for maven4 move to Lookup instead
    private PlexusContainer container;

    /**
     * Execute goal.
     *
     * @throws MojoExecutionException execution of the main class or one of the threads it generated failed.
     * @throws MojoFailureException something bad happened...
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (isSkip()) {
            getLog().info("skipping execute as per configuration");
            return;
        }

        if (null == arguments) {
            arguments = new String[0];
        }

        if (getLog().isDebugEnabled()) {
            StringBuffer msg = new StringBuffer("Invoking : ");
            msg.append(mainClass);
            msg.append(".main(");
            for (int i = 0; i < arguments.length; i++) {
                if (i > 0) {
                    msg.append(", ");
                }
                msg.append(arguments[i]);
            }
            msg.append(")");
            getLog().debug(msg);
        }

        if (preloadCommonPool >= 0) {
            preloadCommonPool();
        }

        IsolatedThreadGroup threadGroup = new IsolatedThreadGroup(mainClass /* name */);
        // TODO:
        //   Adjust implementation for future JDKs after removal of SecurityManager.
        //   See https://openjdk.org/jeps/411 for basic information.
        //   See https://bugs.openjdk.org/browse/JDK-8199704 for details about how users might be able to
        // block
        //   System::exit in post-removal JDKs (still undecided at the time of writing this comment).
        Thread bootstrapThread = new Thread( // TODO: drop this useless thread 99% of the time
                threadGroup,
                () -> {
                    int sepIndex = mainClass.indexOf('/');

                    final String bootClassName;
                    if (sepIndex >= 0) {
                        bootClassName = mainClass.substring(sepIndex + 1);
                    } else {
                        bootClassName = mainClass;
                    }

                    try {
                        doExec(bootClassName);
                    } catch (IllegalAccessException | NoSuchMethodException | NoSuchMethodError e) { // just pass it on
                        Thread.currentThread()
                                .getThreadGroup()
                                .uncaughtException(
                                        Thread.currentThread(),
                                        new Exception(
                                                "The specified mainClass doesn't contain a main method with appropriate signature.",
                                                e));
                    } catch (InvocationTargetException e) {
                        // use the cause if available to improve the plugin execution output
                        Throwable exceptionToReport = e.getCause() != null ? e.getCause() : e;
                        Thread.currentThread()
                                .getThreadGroup()
                                .uncaughtException(Thread.currentThread(), exceptionToReport);
                    } catch (SystemExitException systemExitException) {
                        getLog().info(systemExitException.getMessage());
                        if (systemExitException.getExitCode() != 0) {
                            throw systemExitException;
                        }
                    } catch (Throwable e) { // just pass it on
                        Thread.currentThread().getThreadGroup().uncaughtException(Thread.currentThread(), e);
                    }
                },
                mainClass + ".main()");
        URLClassLoader classLoader = getClassLoader(); // TODO: enable to cache accross executions
        bootstrapThread.setContextClassLoader(classLoader);
        setSystemProperties();

        bootstrapThread.start();
        joinNonDaemonThreads(threadGroup);
        // It's plausible that spontaneously a non-daemon thread might be created as we try and shut down,
        // but it's too late since the termination condition (only daemon threads) has been triggered.
        if (keepAlive) {
            getLog().warn(
                            "Warning: keepAlive is now deprecated and obsolete. Do you need it? Please comment on MEXEC-6.");
            waitFor(0);
        }

        if (cleanupDaemonThreads) {

            terminateThreads(threadGroup);

            try {
                threadGroup.destroy();
            } catch (RuntimeException | Error /* missing method in future java version */ e) {
                getLog().warn("Couldn't destroy threadgroup " + threadGroup, e);
            }
        }

        if (classLoader != null) {
            try {
                classLoader.close();
            } catch (IOException e) {
                getLog().error(e.getMessage(), e);
            }
        }

        if (originalSystemProperties != null) {
            System.setProperties(originalSystemProperties);
        }

        synchronized (threadGroup) {
            if (threadGroup.uncaughtException != null) {
                throw new MojoExecutionException(
                        "An exception occurred while executing the Java class. "
                                + threadGroup.uncaughtException.getMessage(),
                        threadGroup.uncaughtException);
            }
        }

        registerSourceRoots();
    }

    private void doExec(final String bootClassName) throws Throwable {
        Class<?> bootClass = Thread.currentThread().getContextClassLoader().loadClass(bootClassName);
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            doMain(lookup.findStatic(bootClass, "main", MethodType.methodType(void.class, String[].class)));
        } catch (final NoSuchMethodException nsme) {
            if (Runnable.class.isAssignableFrom(bootClass)) {
                doRun(bootClass);
            } else {
                throw nsme;
            }
        }
    }

    private void doMain(final MethodHandle mainHandle) throws Throwable {
        mainHandle.invoke(arguments);
    }

    private void doRun(final Class<?> bootClass)
            throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        final Class<? extends Runnable> runnableClass = bootClass.asSubclass(Runnable.class);
        final Constructor<? extends Runnable> constructor = Stream.of(runnableClass.getDeclaredConstructors())
                .map(i -> (Constructor<? extends Runnable>) i)
                .filter(i -> Modifier.isPublic(i.getModifiers()))
                .max(Comparator.<Constructor<? extends Runnable>, Integer>comparing(Constructor::getParameterCount))
                .orElseThrow(() -> new IllegalArgumentException("No public constructor found for " + bootClass));
        if (getLog().isDebugEnabled()) {
            getLog().debug("Using constructor " + constructor);
        }

        Runnable runnable;
        try { // todo: enhance that but since injection API is being defined at mvn4 level it is
            // good enough
            final Object[] args = Stream.of(constructor.getParameters())
                    .map(param -> {
                        try {
                            return lookupParam(param);
                        } catch (final ComponentLookupException e) {
                            getLog().error(e.getMessage(), e);
                            throw new IllegalStateException(e);
                        }
                    })
                    .toArray(Object[]::new);
            constructor.setAccessible(true);
            runnable = constructor.newInstance(args);
        } catch (final RuntimeException re) {
            if (getLog().isDebugEnabled()) {
                getLog().debug(
                                "Can't inject " + runnableClass + "': " + re.getMessage() + ", will ignore injections",
                                re);
            }
            final Constructor<? extends Runnable> declaredConstructor = runnableClass.getDeclaredConstructor();
            declaredConstructor.setAccessible(true);
            runnable = declaredConstructor.newInstance();
        }
        runnable.run();
    }

    private Object lookupParam(final java.lang.reflect.Parameter param) throws ComponentLookupException {
        final String name = param.getName();
        switch (name) {
                // loose coupled to maven (wrapped with standard jvm types to not require it)
            case "systemProperties": // Properties
                return getSession().getSystemProperties();
            case "systemPropertiesUpdater": // BiConsumer<String, String>
                return propertiesUpdater(getSession().getSystemProperties());
            case "userProperties": // Properties
                return getSession().getUserProperties();
            case "userPropertiesUpdater": // BiConsumer<String, String>
                return propertiesUpdater(getSession().getUserProperties());
            case "projectProperties": // Properties
                return project.getProperties();
            case "projectPropertiesUpdater": // BiConsumer<String, String>
                return propertiesUpdater(project.getProperties());
            case "highestVersionResolver": // Function<String, String>
                return resolveVersion(VersionRangeResult::getHighestVersion);
                // standard bindings
            case "session": // MavenSession
                return getSession();
            case "container": // PlexusContainer
                return container;
            default: // Any
                return lookup(param, name);
        }
    }

    private Object lookup(final java.lang.reflect.Parameter param, final String name) throws ComponentLookupException {
        // try injecting a real instance but loose coupled - will use reflection
        if (param.getType() == Object.class && name.contains("_")) {
            final ClassLoader loader = Thread.currentThread().getContextClassLoader();

            try {
                final int hintIdx = name.indexOf("__hint_");
                if (hintIdx > 0) {
                    final String hint = name.substring(hintIdx + "__hint_".length());
                    final String typeName = name.substring(0, hintIdx).replace('_', '.');
                    return container.lookup(loader.loadClass(typeName), hint);
                }

                final String typeName = name.replace('_', '.');
                return container.lookup(loader.loadClass(typeName));
            } catch (final ClassNotFoundException cnfe) {
                if (getLog().isDebugEnabled()) {
                    getLog().debug("Can't load param (" + name + "): " + cnfe.getMessage(), cnfe);
                }
                // let's try to lookup object, unlikely but not impossible
            }
        }

        // just lookup by type
        return container.lookup(param.getType());
    }

    private Function<String, String> resolveVersion(final Function<VersionRangeResult, Object> fn) {
        return ga -> {
            final int sep = ga.indexOf(':');
            if (sep < 0) {
                throw new IllegalArgumentException("Invalid groupId:artifactId argument: '" + ga + "'");
            }

            final org.eclipse.aether.artifact.Artifact artifact = new DefaultArtifact(ga + ":[0,)");
            final VersionRangeRequest rangeRequest = new VersionRangeRequest();
            rangeRequest.setArtifact(artifact);
            try {
                if (includePluginDependencies && includeProjectDependencies) {
                    rangeRequest.setRepositories(Stream.concat(
                                    project.getRemoteProjectRepositories().stream(),
                                    project.getRemotePluginRepositories().stream())
                            .distinct()
                            .collect(toList()));
                } else if (includePluginDependencies) {
                    rangeRequest.setRepositories(project.getRemotePluginRepositories());
                } else if (includeProjectDependencies) {
                    rangeRequest.setRepositories(project.getRemoteProjectRepositories());
                }
                final VersionRangeResult rangeResult =
                        repositorySystem.resolveVersionRange(getSession().getRepositorySession(), rangeRequest);
                return String.valueOf(fn.apply(rangeResult));
            } catch (final VersionRangeResolutionException e) {
                throw new IllegalStateException(e);
            }
        };
    }

    private BiConsumer<String, String> propertiesUpdater(final Properties props) {
        return (k, v) -> {
            if (v == null) {
                props.remove(k);
            } else {
                props.setProperty(k, v);
            }
        };
    }

    /**
     * To avoid the exec:java to consider common pool threads leaked, let's pre-create them.
     */
    private void preloadCommonPool() {
        try {
            // ensure common pool exists in the jvm
            final ExecutorService es = ForkJoinPool.commonPool();
            final int max = preloadCommonPool > 0 ? preloadCommonPool : ForkJoinPool.getCommonPoolParallelism();
            final CountDownLatch preLoad = new CountDownLatch(1);
            for (int i = 0; i < max; i++) {
                es.submit(() -> {
                    try {
                        preLoad.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            preLoad.countDown();
        } catch (final Exception e) {
            getLog().debug(e.getMessage() + ", skipping commonpool earger init");
        }
    }

    /**
     * a ThreadGroup to isolate execution and collect exceptions.
     */
    class IsolatedThreadGroup extends ThreadGroup {
        private Throwable uncaughtException; // synchronize access to this

        public IsolatedThreadGroup(String name) {
            super(name);
        }

        public void uncaughtException(Thread thread, Throwable throwable) {
            if (throwable instanceof ThreadDeath) {
                return; // harmless
            }
            synchronized (this) {
                if (uncaughtException == null) // only remember the first one
                {
                    uncaughtException = throwable; // will be reported eventually
                }
            }
            getLog().warn(throwable);
        }
    }

    private void joinNonDaemonThreads(ThreadGroup threadGroup) {
        boolean foundNonDaemon;
        do {
            foundNonDaemon = false;
            Collection<Thread> threads = getActiveThreads(threadGroup);
            for (Thread thread : threads) {
                if (thread.isDaemon()) {
                    continue;
                }
                foundNonDaemon = true; // try again; maybe more threads were created while we were busy
                joinThread(thread, 0);
            }
        } while (foundNonDaemon);
    }

    private void joinThread(Thread thread, long timeoutMsecs) {
        try {
            getLog().debug("joining on thread " + thread);
            thread.join(timeoutMsecs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // good practice if don't throw
            getLog().warn("interrupted while joining against thread " + thread, e); // not expected!
        }
        if (thread.isAlive()) // generally abnormal
        {
            getLog().warn("thread " + thread + " was interrupted but is still alive after waiting at least "
                    + timeoutMsecs + "msecs");
        }
    }

    private void terminateThreads(ThreadGroup threadGroup) {
        long startTime = System.currentTimeMillis();
        Set<Thread> uncooperativeThreads = new HashSet<>(); // these were not responsive to interruption
        for (Collection<Thread> threads = getActiveThreads(threadGroup);
                !threads.isEmpty();
                threads = getActiveThreads(threadGroup), threads.removeAll(uncooperativeThreads)) {
            // Interrupt all threads we know about as of this instant (harmless if spuriously went dead (! isAlive())
            // or if something else interrupted it ( isInterrupted() ).
            for (Thread thread : threads) {
                getLog().debug("interrupting thread " + thread);
                thread.interrupt();
            }
            // Now join with a timeout and call stop() (assuming flags are set right)
            boolean threadStopIsAvailable = true;
            for (Thread thread : threads) {
                if (!thread.isAlive()) {
                    continue; // and, presumably it won't show up in getActiveThreads() next iteration
                }
                if (daemonThreadJoinTimeout <= 0) {
                    joinThread(thread, 0); // waits until not alive; no timeout
                    continue;
                }
                long timeout = daemonThreadJoinTimeout - (System.currentTimeMillis() - startTime);
                if (timeout > 0) {
                    joinThread(thread, timeout);
                }
                if (!thread.isAlive()) {
                    continue;
                }
                uncooperativeThreads.add(thread); // ensure we don't process again
                if (stopUnresponsiveDaemonThreads && threadStopIsAvailable) {
                    getLog().warn("thread " + thread + " will be Thread.stop()'ed");
                    try {
                        thread.stop();
                    } catch (UnsupportedOperationException unsupportedOperationException) {
                        threadStopIsAvailable = false;
                        getLog().warn(THREAD_STOP_UNAVAILABLE);
                    }
                } else {
                    getLog().warn("thread " + thread + " will linger despite being asked to die via interruption");
                }
            }
        }
        if (!uncooperativeThreads.isEmpty()) {
            getLog().warn("NOTE: " + uncooperativeThreads.size() + " thread(s) did not finish despite being asked to"
                    + " via interruption. This is not a problem with exec:java, it is a problem with the running code."
                    + " Although not serious, it should be remedied.");
        } else {
            int activeCount = threadGroup.activeCount();
            if (activeCount != 0) {
                // TODO this may be nothing; continue on anyway; perhaps don't even log in future
                Thread[] threadsArray = new Thread[1];
                threadGroup.enumerate(threadsArray);
                getLog().debug("strange; " + activeCount + " thread(s) still active in the group " + threadGroup
                        + " such as " + threadsArray[0]);
            }
        }
    }

    private Collection<Thread> getActiveThreads(ThreadGroup threadGroup) {
        Thread[] threads = new Thread[threadGroup.activeCount()];
        int numThreads = threadGroup.enumerate(threads);
        Collection<Thread> result = new ArrayList<>(numThreads);
        for (int i = 0; i < threads.length && threads[i] != null; i++) {
            result.add(threads[i]);
        }
        return result; // note: result should be modifiable
    }

    /**
     * Pass any given system properties to the java system properties.
     */
    private void setSystemProperties() {
        if (systemProperties == null) {
            return;
        }
        // copy otherwise the restore phase does nothing
        originalSystemProperties = new Properties();
        originalSystemProperties.putAll(System.getProperties());

        if (Stream.of(systemProperties).anyMatch(it -> it instanceof ProjectProperties)) {
            System.getProperties().putAll(project.getProperties());
        }

        for (AbstractProperty systemProperty : systemProperties) {
            if (!(systemProperty instanceof Property)) {
                continue;
            }

            Property prop = (Property) systemProperty;
            String value = prop.getValue();
            System.setProperty(prop.getKey(), value == null ? "" : value);
        }
    }

    /**
     * Set up a classloader for the execution of the main class.
     *
     * @return the classloader
     * @throws MojoExecutionException if a problem happens
     */
    private URLClassLoader getClassLoader() throws MojoExecutionException {
        List<Path> classpathURLs = new ArrayList<>();
        this.addRelevantPluginDependenciesToClasspath(classpathURLs);
        this.addRelevantProjectDependenciesToClasspath(classpathURLs);
        this.addAdditionalClasspathElements(classpathURLs);

        try {
            final URLClassLoaderBuilder builder = URLClassLoaderBuilder.builder()
                    .setLogger(getLog())
                    .setPaths(classpathURLs)
                    .setExclusions(classpathFilenameExclusions);
            if (blockSystemExit) {
                builder.setTransformer(new BlockExitTransformer());
            }
            return builder.build();
        } catch (NullPointerException | IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void addAdditionalClasspathElements(List<Path> path) {
        if (additionalClasspathElements != null) {
            for (String classPathElement : additionalClasspathElements) {
                Path file = Paths.get(classPathElement);
                if (!file.isAbsolute()) {
                    file = project.getBasedir().toPath().resolve(file);
                }
                getLog().debug("Adding additional classpath element: " + file + " to classpath");
                path.add(file);
            }
        }
    }

    /**
     * Add any relevant project dependencies to the classpath. Indirectly takes includePluginDependencies and
     * ExecutableDependency into consideration.
     *
     * @param path classpath of {@link java.net.URL} objects
     * @throws MojoExecutionException if a problem happens
     */
    private void addRelevantPluginDependenciesToClasspath(List<Path> path) throws MojoExecutionException {
        if (hasCommandlineArgs()) {
            arguments = parseCommandlineArgs();
        }

        for (Artifact classPathElement : this.determineRelevantPluginDependencies()) {
            getLog().debug("Adding plugin dependency artifact: " + classPathElement.getArtifactId() + " to classpath");
            path.add(classPathElement.getFile().toPath());
        }
    }

    /**
     * Add any relevant project dependencies to the classpath. Takes includeProjectDependencies into consideration.
     *
     * @param path classpath of {@link java.net.URL} objects
     */
    private void addRelevantProjectDependenciesToClasspath(List<Path> path) {
        if (this.includeProjectDependencies) {
            getLog().debug("Project Dependencies will be included.");

            List<Artifact> artifacts = new ArrayList<>();
            List<Path> theClasspathFiles = new ArrayList<>();

            collectProjectArtifactsAndClasspath(artifacts, theClasspathFiles);

            for (Path classpathFile : theClasspathFiles) {
                getLog().debug("Adding to classpath : " + classpathFile);
                path.add(classpathFile);
            }

            for (Artifact classPathElement : artifacts) {
                getLog().debug("Adding project dependency artifact: " + classPathElement.getArtifactId()
                        + " to classpath");
                path.add(classPathElement.getFile().toPath());
            }
        } else {
            getLog().debug("Project Dependencies will be excluded.");
        }
    }

    /**
     * Determine all plugin dependencies relevant to the executable. Takes includePlugins, and the executableDependency
     * into consideration.
     *
     * @return a set of Artifact objects. (Empty set is returned if there are no relevant plugin dependencies.)
     * @throws MojoExecutionException if a problem happens resolving the plufin dependencies
     */
    private Set<Artifact> determineRelevantPluginDependencies() throws MojoExecutionException {
        Set<Artifact> relevantDependencies;
        if (this.includePluginDependencies) {
            if (this.executableDependency == null) {
                getLog().debug("All Plugin Dependencies will be included.");
                relevantDependencies = new HashSet<>(this.getPluginDependencies());
            } else {
                getLog().debug("Selected plugin Dependencies will be included.");
                Artifact executableArtifact = this.findExecutableArtifact();
                relevantDependencies = this.resolveExecutableDependencies(executableArtifact);
            }
        } else {
            relevantDependencies = Collections.emptySet();
            getLog().debug("Plugin Dependencies will be excluded.");
        }
        return relevantDependencies;
    }

    /**
     * Resolve the executable dependencies for the specified project
     *
     * @param executableArtifact the executable plugin dependency
     * @return a set of Artifacts
     * @throws MojoExecutionException if a failure happens
     */
    private Set<Artifact> resolveExecutableDependencies(Artifact executableArtifact) throws MojoExecutionException {
        try {
            CollectRequest collectRequest = new CollectRequest();
            collectRequest.setRoot(new Dependency(RepositoryUtils.toArtifact(executableArtifact), classpathScope));
            collectRequest.setRepositories(project.getRemotePluginRepositories());

            DependencyFilter classpathFilter = DependencyFilterUtils.classpathFilter(classpathScope);

            DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, classpathFilter);

            DependencyResult dependencyResult =
                    repositorySystem.resolveDependencies(getSession().getRepositorySession(), dependencyRequest);

            return dependencyResult.getArtifactResults().stream()
                    .map(ArtifactResult::getArtifact)
                    .map(RepositoryUtils::toArtifact)
                    .collect(Collectors.toSet());
        } catch (DependencyResolutionException ex) {
            throw new MojoExecutionException(
                    "Encountered problems resolving dependencies of the executable "
                            + "in preparation for its execution.",
                    ex);
        }
    }

    /**
     * Stop program execution for nn millis.
     *
     * @param millis the number of millis-seconds to wait for, <code>0</code> stops program forever.
     */
    private void waitFor(long millis) {
        Object lock = new Object();
        synchronized (lock) {
            try {
                lock.wait(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // good practice if don't throw
                getLog().warn("Spuriously interrupted while waiting for " + millis + "ms", e);
            }
        }
    }
}
