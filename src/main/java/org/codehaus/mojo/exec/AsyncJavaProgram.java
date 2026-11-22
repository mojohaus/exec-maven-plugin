package org.codehaus.mojo.exec;

/*
 * Copyright MojoHaus and Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.net.URLClassLoader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.maven.plugin.MojoExecutionException;

/**
 * A program that {@code exec:java} runs in the background. The execution that starts it keeps this handle in the
 * plugin context of the project, under the id of the execution, so that {@code exec:java-stop} of the same project
 * finds it. The plugin context is not shared between projects, so parallel builds do not see each other's programs.
 *
 * @since 3.8.0
 */
final class AsyncJavaProgram {

    private static final String CONTEXT_KEY = AsyncJavaProgram.class.getName() + ".programs";

    /** What the stop waits for the threads, when the configured timeout is not a positive number. */
    private static final long DEFAULT_STOP_TIMEOUT_MILLIS = 15000;

    private final String executionId;
    private final AbstractExecJavaBase owner;
    private final AbstractExecJavaBase.IsolatedThreadGroup threadGroup;
    private final Thread bootstrapThread;
    private final URLClassLoader classLoader;
    private final SystemPropertyChanges systemProperties;
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final Thread shutdownHook;

    AsyncJavaProgram(
            String executionId,
            AbstractExecJavaBase owner,
            AbstractExecJavaBase.IsolatedThreadGroup threadGroup,
            Thread bootstrapThread,
            URLClassLoader classLoader,
            SystemPropertyChanges systemProperties) {
        this.executionId = executionId;
        this.owner = owner;
        this.threadGroup = threadGroup;
        this.bootstrapThread = bootstrapThread;
        this.classLoader = classLoader;
        this.systemProperties = systemProperties;
        this.shutdownHook = new Thread(() -> stopQuietly(), "exec-java-stop-" + executionId);
    }

    String getExecutionId() {
        return executionId;
    }

    /**
     * @return the programs that are running in the project of the plugin context, by execution id, in start order
     */
    @SuppressWarnings("unchecked")
    private static Map<String, AsyncJavaProgram> programs(Map<String, Object> pluginContext) {
        synchronized (pluginContext) {
            Map<String, AsyncJavaProgram> programs = (Map<String, AsyncJavaProgram>) pluginContext.get(CONTEXT_KEY);
            if (programs == null) {
                programs = new LinkedHashMap<>();
                pluginContext.put(CONTEXT_KEY, programs);
            }
            return programs;
        }
    }

    /**
     * Fails if an execution with this id still has a program running, as it could not be told apart from the new one.
     */
    static void checkNotRunning(Map<String, Object> pluginContext, String executionId) throws MojoExecutionException {
        Map<String, AsyncJavaProgram> programs = programs(pluginContext);
        synchronized (pluginContext) {
            if (programs.containsKey(executionId)) {
                throw new MojoExecutionException("The asynchronous Java program of the execution '" + executionId
                        + "' is still running. Stop it with exec:java-stop before starting it again.");
            }
        }
    }

    void register(Map<String, Object> pluginContext) throws MojoExecutionException {
        Map<String, AsyncJavaProgram> programs = programs(pluginContext);
        synchronized (pluginContext) {
            checkNotRunning(pluginContext, executionId);
            programs.put(executionId, this);
        }
        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } catch (IllegalStateException e) {
            // the JVM is shutting down already: nothing will survive it
        }
    }

    /**
     * @return the running program with this id, or <code>null</code>
     */
    static AsyncJavaProgram find(Map<String, Object> pluginContext, String executionId) {
        Map<String, AsyncJavaProgram> programs = programs(pluginContext);
        synchronized (pluginContext) {
            return programs.get(executionId);
        }
    }

    /**
     * @return the running programs of the project, the latest started first
     */
    static List<AsyncJavaProgram> findAll(Map<String, Object> pluginContext) {
        Map<String, AsyncJavaProgram> programs = programs(pluginContext);
        synchronized (pluginContext) {
            List<AsyncJavaProgram> all = new ArrayList<>(programs.values());
            java.util.Collections.reverse(all);
            return all;
        }
    }

    /**
     * @return the first exception the program did not handle, or <code>null</code>
     */
    Throwable getFailure() {
        return threadGroup.getUncaughtException();
    }

    /**
     * @return <code>true</code> while the main method runs or any other thread of the program is alive
     */
    boolean isAlive() {
        return bootstrapThread.isAlive() || threadGroup.activeCount() > 0;
    }

    Thread getShutdownHook() {
        return shutdownHook;
    }

    /**
     * Stops the program: interrupts its threads and waits for them for the time {@code daemonThreadJoinTimeout} says,
     * destroys the thread group, closes the class loader unless {@code keepClassLoaderOpen} is set, and restores the
     * system properties. Does nothing if the program is stopped already.
     *
     * @param pluginContext the plugin context the program is registered in
     * @throws MojoExecutionException if the program threw
     */
    void stop(Map<String, Object> pluginContext) throws MojoExecutionException {
        unregister(pluginContext);
        Throwable failure = cleanup();
        if (failure != null) {
            throw new MojoExecutionException(
                    "An exception occurred while executing the Java class of the execution '" + executionId + "'. "
                            + failure.getMessage(),
                    failure);
        }
    }

    private void unregister(Map<String, Object> pluginContext) {
        Map<String, AsyncJavaProgram> programs = programs(pluginContext);
        synchronized (pluginContext) {
            programs.remove(executionId, this);
        }
    }

    /**
     * Stops a program that failed to start and gives its failure back, so that the caller reports the real cause.
     *
     * @return the failure of the program, or <code>null</code>
     */
    Throwable abort(Map<String, Object> pluginContext) {
        unregister(pluginContext);
        Throwable failure = getFailure();
        cleanup();
        return failure;
    }

    /**
     * The fallback for a build that ends without exec:java-stop. It never throws and never waits for longer than the
     * stop does.
     */
    private void stopQuietly() {
        try {
            Throwable failure = cleanup();
            if (failure != null) {
                owner.getLog().warn("The Java class of the execution '" + executionId + "' failed: " + failure);
            }
        } catch (RuntimeException | Error e) {
            // the JVM is going down; nothing useful is left to do
        }
    }

    /**
     * @return the failure of the program, or <code>null</code>
     */
    private Throwable cleanup() {
        if (!stopped.compareAndSet(false, true)) {
            return null;
        }
        if (Thread.currentThread() != shutdownHook) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException e) {
                // already shutting down: this is the hook, or it is about to run and finds nothing left to do
            }
        }
        Throwable failure = getFailure();
        owner.terminateThreads(threadGroup, stopTimeout());
        if (owner.cleanupDaemonThreads) {
            try {
                threadGroup.destroy();
            } catch (RuntimeException | Error /* missing method in future java version */ e) {
                owner.getLog().warn("Couldn't destroy threadgroup " + threadGroup, e);
            }
        }
        if (classLoader != null && !owner.keepClassLoaderOpen) {
            try {
                classLoader.close();
            } catch (IOException e) {
                owner.getLog().error(e.getMessage(), e);
            }
        }
        if (systemProperties != null) {
            systemProperties.restore();
        }
        Throwable late = getFailure();
        return failure != null ? failure : late;
    }

    private long stopTimeout() {
        return owner.daemonThreadJoinTimeout > 0 ? owner.daemonThreadJoinTimeout : DEFAULT_STOP_TIMEOUT_MILLIS;
    }

    /**
     * The system properties one background program set. Programs running at the same time may set the same key, so
     * the value before the first of them and every program's value are kept per key for the whole JVM: stopping a
     * program drops its value, and the key goes back to the value of the newest program still running, or to the
     * value before all of them.
     */
    static final class SystemPropertyChanges {
        private static final Map<String, KeyHistory> HISTORY = new HashMap<>();

        private final Map<String, Value> own = new LinkedHashMap<>();

        void set(String key, String value) {
            synchronized (HISTORY) {
                KeyHistory history = HISTORY.computeIfAbsent(key, k -> new KeyHistory(System.getProperty(k)));
                Value previous = own.get(key);
                if (previous != null) {
                    history.values.remove(previous);
                }
                Value applied = new Value(value);
                own.put(key, applied);
                history.values.addLast(applied);
                System.setProperty(key, value);
            }
        }

        void restore() {
            synchronized (HISTORY) {
                for (Map.Entry<String, Value> entry : own.entrySet()) {
                    String key = entry.getKey();
                    Value value = entry.getValue();
                    KeyHistory history = HISTORY.get(key);
                    boolean newest = history.values.peekLast() == value;
                    history.values.remove(value);
                    // a value someone else set after this program stays
                    if (newest && Objects.equals(System.getProperty(key), value.value)) {
                        Value next = history.values.peekLast();
                        String old = next != null ? next.value : history.before;
                        if (old == null) {
                            System.clearProperty(key);
                        } else {
                            System.setProperty(key, old);
                        }
                    }
                    if (history.values.isEmpty()) {
                        HISTORY.remove(key);
                    }
                }
                own.clear();
            }
        }

        /** One program's value; compared by identity, since two programs may set the same value. */
        private static final class Value {
            private final String value;

            Value(String value) {
                this.value = value;
            }
        }

        private static final class KeyHistory {
            private final String before;
            private final Deque<Value> values = new ArrayDeque<>();

            KeyHistory(String before) {
                this.before = before;
            }
        }
    }
}
