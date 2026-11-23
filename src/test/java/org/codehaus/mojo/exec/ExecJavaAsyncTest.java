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
package org.codehaus.mojo.exec;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.maven.api.plugin.testing.MojoExtension.setVariableValueToObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The <code>async</code> mode of <code>exec:java</code> and the <code>java-stop</code> goal.
 */
@MojoTest
class ExecJavaAsyncTest {

    private static final Duration LIMIT = Duration.ofSeconds(60);

    private final Map<String, Object> pluginContext = new HashMap<>();

    @BeforeEach
    void resetFixtures() {
        AsyncTestMain.failNow = new java.util.concurrent.CountDownLatch(1);
        AsyncTestMain.release = new java.util.concurrent.CountDownLatch(1);
        AsyncTestMain.interrupted = new java.util.concurrent.CountDownLatch(1);
    }

    private static final java.util.Set<Integer> HANDED_OUT = new java.util.HashSet<>();

    /** A free port that no other call of this JVM got, as the OS may hand out the same closed port twice. */
    private static synchronized int freePort() throws IOException {
        while (true) {
            try (ServerSocket socket = new ServerSocket(0)) {
                if (HANDED_OUT.add(socket.getLocalPort())) {
                    return socket.getLocalPort();
                }
            }
        }
    }

    private static boolean isListening(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void asyncProgram(ExecJavaMojo mojo, String mainClass, String check, String... arguments) throws Exception {
        setVariableValueToObject(mojo, "async", true);
        setVariableValueToObject(mojo, "mainClass", mainClass);
        setVariableValueToObject(mojo, "arguments", arguments);
        setVariableValueToObject(mojo, "asyncStartCheck", check);
        setVariableValueToObject(mojo, "asyncStartCheckPollInterval", 50L);
        mojo.setPluginContext(pluginContext);
    }

    private void stop(String startExecutionId) throws Exception {
        ExecJavaStopMojo stop = new ExecJavaStopMojo();
        stop.setPluginContext(pluginContext);
        if (startExecutionId != null) {
            setVariableValueToObject(stop, "startExecutionId", startExecutionId);
        }
        stop.execute();
    }

    private AsyncJavaProgram onlyProgram() {
        assertEquals(1, AsyncJavaProgram.findAll(pluginContext).size());
        return AsyncJavaProgram.findAll(pluginContext).get(0);
    }

    @Test
    @InjectMojo(goal = "java")
    void httpCheckWaitsForTheServerAndStopEndsIt(ExecJavaMojo mojo) throws Exception {
        assertTimeoutPreemptively(LIMIT, () -> {
            int port = freePort();
            asyncProgram(
                    mojo,
                    "org.codehaus.mojo.exec.TestServerMain",
                    "http://localhost:" + port + "/test",
                    String.valueOf(port));

            mojo.execute();

            HttpURLConnection connection =
                    (HttpURLConnection) new URL("http://localhost:" + port + "/test").openConnection();
            assertEquals(200, connection.getResponseCode());
            AsyncJavaProgram program = onlyProgram();
            assertTrue(program.isAlive());

            stop(null);

            assertTrue(TestServerMain.stopped.await(10, TimeUnit.SECONDS));
            assertFalse(program.isAlive());
            assertFalse(isListening(port));
            assertTrue(AsyncJavaProgram.findAll(pluginContext).isEmpty());
        });
    }

    @Test
    @InjectMojo(goal = "java")
    void tcpCheckWaitsForThePort(ExecJavaMojo mojo) throws Exception {
        assertTimeoutPreemptively(LIMIT, () -> {
            int port = freePort();
            asyncProgram(mojo, "org.codehaus.mojo.exec.AsyncTestMain", "tcp://localhost:" + port, "tcp", "" + port);

            mojo.execute();

            assertTrue(isListening(port));
            stop(null);
            assertTrue(AsyncTestMain.interrupted.await(10, TimeUnit.SECONDS));
            assertFalse(isListening(port));
        });
    }

    @Test
    @InjectMojo(goal = "java")
    void withoutACheckTheGoalReturnsAtOnceAndStopCleansUp(ExecJavaMojo mojo) throws Exception {
        assertTimeoutPreemptively(LIMIT, () -> {
            int port = freePort();
            asyncProgram(mojo, "org.codehaus.mojo.exec.AsyncTestMain", null, "tcp", "" + port);

            mojo.execute();
            AsyncJavaProgram program = onlyProgram();

            stop(null);
            assertFalse(program.isAlive());
            assertTrue(AsyncTestMain.interrupted.await(10, TimeUnit.SECONDS));
        });
    }

    @Test
    @InjectMojo(goal = "java")
    void startFailsWhenTheProgramThrowsBeforeItIsReady(ExecJavaMojo mojo) throws Exception {
        assertTimeoutPreemptively(LIMIT, () -> {
            asyncProgram(mojo, "org.codehaus.mojo.exec.AsyncTestMain", "tcp://localhost:" + freePort(), "fail");

            MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);

            assertTrue(e.getMessage().contains("boom"), e.getMessage());
            assertTrue(e.getCause() instanceof IllegalStateException);
            assertTrue(AsyncJavaProgram.findAll(pluginContext).isEmpty());
        });
    }

    @Test
    @InjectMojo(goal = "java")
    void startFailsWhenTheProgramEndsBeforeItIsReady(ExecJavaMojo mojo) throws Exception {
        assertTimeoutPreemptively(LIMIT, () -> {
            asyncProgram(mojo, "org.codehaus.mojo.exec.AsyncTestMain", "tcp://localhost:" + freePort(), "finish");

            MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);

            assertTrue(e.getMessage().contains("ended before it was ready"), e.getMessage());
            assertTrue(AsyncJavaProgram.findAll(pluginContext).isEmpty());
        });
    }

    @Test
    @InjectMojo(goal = "java")
    void startFailsAfterTheTimeoutAndStopsTheProgram(ExecJavaMojo mojo) throws Exception {
        assertTimeoutPreemptively(LIMIT, () -> {
            int port = freePort();
            // the program listens on the port, but the check asks for another one
            asyncProgram(
                    mojo, "org.codehaus.mojo.exec.AsyncTestMain", "tcp://localhost:" + freePort(), "tcp", "" + port);
            setVariableValueToObject(mojo, "asyncStartTimeout", 600L);

            long start = System.nanoTime();
            MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);

            assertTrue(e.getMessage().contains("not ready after 600 ms"), e.getMessage());
            assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 30);
            assertTrue(AsyncTestMain.interrupted.await(10, TimeUnit.SECONDS), "the program was stopped");
            assertFalse(isListening(port));
            assertTrue(AsyncJavaProgram.findAll(pluginContext).isEmpty());
        });
    }

    @Test
    @InjectMojo(goal = "java")
    void stopFailsWhenTheProgramThrewAfterItWasReady(ExecJavaMojo mojo) throws Exception {
        assertTimeoutPreemptively(LIMIT, () -> {
            int port = freePort();
            asyncProgram(
                    mojo, "org.codehaus.mojo.exec.AsyncTestMain", "tcp://localhost:" + port, "late-fail", "" + port);
            mojo.execute();
            AsyncJavaProgram program = onlyProgram();

            AsyncTestMain.failNow.countDown();
            while (program.getFailure() == null) {
                Thread.sleep(20);
            }

            MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> stop(null));
            assertTrue(e.getMessage().contains("late boom"), e.getMessage());
            assertTrue(AsyncJavaProgram.findAll(pluginContext).isEmpty());
            // a second stop has nothing left to report
            stop(null);
        });
    }

    @Test
    @InjectMojo(goal = "java")
    void executionsAreKeyedByTheirId(ExecJavaMojo first) throws Exception {
        assertTimeoutPreemptively(LIMIT, () -> {
            int port = freePort();
            asyncProgram(first, "org.codehaus.mojo.exec.AsyncTestMain", null, "tcp", "" + port);
            first.execute();
            AsyncJavaProgram program = onlyProgram();

            // the same execution again, while its program runs
            MojoExecutionException e = assertThrows(MojoExecutionException.class, first::execute);
            assertTrue(e.getMessage().contains("still running"), e.getMessage());
            assertTrue(program.isAlive(), "the running program is untouched");

            stop("some-other-id"); // no program under that id: nothing to stop, not an error
            assertTrue(program.isAlive());

            stop(program.getExecutionId());
            assertFalse(program.isAlive());
        });
    }

    @Test
    @InjectMojo(goal = "java")
    void anotherProjectDoesNotSeeTheProgram(ExecJavaMojo mojo) throws Exception {
        assertTimeoutPreemptively(LIMIT, () -> {
            int port = freePort();
            asyncProgram(mojo, "org.codehaus.mojo.exec.AsyncTestMain", null, "tcp", "" + port);
            mojo.execute();
            AsyncJavaProgram program = onlyProgram();

            ExecJavaStopMojo otherProject = new ExecJavaStopMojo();
            otherProject.setPluginContext(new HashMap<>());
            otherProject.execute(); // nothing to stop there
            assertTrue(program.isAlive());

            stop(null);
        });
    }

    @Test
    @InjectMojo(goal = "java")
    void stopWithoutAProgramDoesNothing(ExecJavaMojo mojo) throws Exception {
        stop(null);
    }

    @Test
    @InjectMojo(goal = "java")
    void shutdownHookIsBoundedAndDoesNotWaitForNonDaemonThreads(ExecJavaMojo mojo) throws Exception {
        assertTimeoutPreemptively(LIMIT, () -> {
            asyncProgram(mojo, "org.codehaus.mojo.exec.AsyncTestMain", null, "stubborn");
            setVariableValueToObject(mojo, "daemonThreadJoinTimeout", 500L);
            mojo.execute();
            AsyncJavaProgram program = onlyProgram();
            // the thread ignores the interruption, so joining it would take for ever
            try {
                long start = System.nanoTime();
                program.getShutdownHook().run();
                long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

                assertTrue(AsyncTestMain.interrupted.await(5, TimeUnit.SECONDS), "the thread was interrupted");
                assertTrue(millis < 10000, "the hook took " + millis + " ms");
                assertTrue(program.isAlive(), "an uncooperative thread lingers, as it does without async");
            } finally {
                AsyncTestMain.release.countDown();
            }
        });
    }

    @Test
    @InjectMojo(goal = "java")
    void stopFailsOnLingeringThreadsAfterCleaningUp(ExecJavaMojo mojo) throws Exception {
        assertTimeoutPreemptively(LIMIT, () -> {
            asyncProgram(mojo, "org.codehaus.mojo.exec.AsyncTestMain", null, "stubborn");
            setVariableValueToObject(mojo, "daemonThreadJoinTimeout", 500L);
            setVariableValueToObject(mojo, "failOnLingeringThreads", true);
            Property property = new Property();
            property.setKey("exec.async.lingering.test");
            property.setValue("set");
            setVariableValueToObject(mojo, "systemProperties", new AbstractProperty[] {property});
            mojo.execute();
            assertEquals("set", System.getProperty("exec.async.lingering.test"));
            try {
                MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> stop(null));
                // the exception comes after the cleanup, which restores the properties as its last step
                assertNull(System.getProperty("exec.async.lingering.test"));
                assertTrue(e.getMessage().contains("did not finish within 500 ms"), e.getMessage());
                assertTrue(e.getMessage().contains("execution 'default'"), e.getMessage());
                assertTrue(AsyncJavaProgram.findAll(pluginContext).isEmpty());
            } finally {
                AsyncTestMain.release.countDown();
            }
        });
    }

    @Test
    @InjectMojo(goal = "java")
    void restoresTheSystemProperties(ExecJavaMojo mojo) throws Exception {
        assertTimeoutPreemptively(LIMIT, () -> {
            int port = freePort();
            asyncProgram(mojo, "org.codehaus.mojo.exec.AsyncTestMain", null, "tcp", "" + port);
            Property property = new Property();
            property.setKey("exec.async.test");
            property.setValue("set");
            setVariableValueToObject(mojo, "systemProperties", new AbstractProperty[] {property});

            mojo.execute();
            assertEquals("set", System.getProperty("exec.async.test"));

            stop(null);
            assertNull(System.getProperty("exec.async.test"));
        });
    }

    @Test
    @InjectMojo(goal = "java")
    void invalidCheckFailsBeforeTheProgramStarts(ExecJavaMojo mojo) throws Exception {
        asyncProgram(mojo, "org.codehaus.mojo.exec.AsyncTestMain", "ftp://localhost/", "tcp", "1");

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);

        assertTrue(e.getMessage().contains("tcp://"), e.getMessage());
        assertTrue(AsyncJavaProgram.findAll(pluginContext).isEmpty());
    }

    @Test
    @InjectMojo(goal = "java")
    void groovyScriptCheck(ExecJavaMojo mojo) throws Exception {
        assertTimeoutPreemptively(LIMIT, () -> {
            int port = freePort();
            asyncProgram(
                    mojo,
                    "org.codehaus.mojo.exec.TestServerMain",
                    "try { new URL('http://localhost:" + port + "/test').openConnection().responseCode == 200 }"
                            + " catch (java.net.ConnectException e) { false }",
                    String.valueOf(port));
            setVariableValueToObject(mojo, "asyncStartCheckScriptLanguage", "groovy");

            mojo.execute();
            assertNotNull(AsyncJavaProgram.find(pluginContext, onlyProgram().getExecutionId()));
            assertTrue(isListening(port));

            stop(null);
            assertTrue(TestServerMain.stopped.await(10, TimeUnit.SECONDS));
        });
    }

    @Test
    @InjectMojo(goal = "java")
    void scriptWithoutALanguageEngineFails(ExecJavaMojo mojo) throws Exception {
        asyncProgram(mojo, "org.codehaus.mojo.exec.AsyncTestMain", "true", "finish");
        setVariableValueToObject(mojo, "asyncStartCheckScriptLanguage", "no-such-language");

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);

        assertTrue(e.getMessage().contains("no-such-language"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "java")
    @MojoParameter(name = "mainClass", value = "org.codehaus.mojo.exec.DummyMain")
    void asyncIsOffByDefaultAndNothingIsRegistered(ExecJavaMojo mojo) throws Exception {
        mojo.setPluginContext(pluginContext);

        mojo.execute();

        assertTrue(AsyncJavaProgram.findAll(pluginContext).isEmpty());
    }

    @Test
    @InjectMojo(goal = "java")
    void stopOfAnIdWithoutAProgramSucceeds(ExecJavaMojo mojo) throws Exception {
        stop("never-started");
    }

    @Test
    @InjectMojo(goal = "java")
    void timeoutReportsTheLastFailureOfTheCheck(ExecJavaMojo mojo) throws Exception {
        assertTimeoutPreemptively(LIMIT, () -> {
            int port = freePort();
            asyncProgram(
                    mojo, "org.codehaus.mojo.exec.AsyncTestMain", "tcp://localhost:" + freePort(), "tcp", "" + port);
            setVariableValueToObject(mojo, "asyncStartTimeout", 400L);

            MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);

            assertTrue(e.getMessage().contains("last attempt failed"), e.getMessage());
            assertTrue(e.getCause() instanceof IOException, String.valueOf(e.getCause()));
        });
    }

    @Test
    @InjectMojo(goal = "java")
    void interruptWhileWaitingStopsTheProgramAndKeepsTheFlag(ExecJavaMojo mojo) throws Exception {
        assertTimeoutPreemptively(LIMIT, () -> {
            int port = freePort();
            // never ready, and a long timeout
            asyncProgram(
                    mojo, "org.codehaus.mojo.exec.AsyncTestMain", "tcp://localhost:" + freePort(), "tcp", "" + port);
            setVariableValueToObject(mojo, "asyncStartTimeout", 600000L);
            java.util.concurrent.atomic.AtomicReference<Throwable> thrown =
                    new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.atomic.AtomicBoolean flag = new java.util.concurrent.atomic.AtomicBoolean();
            Thread waiter = new Thread(() -> {
                try {
                    mojo.execute();
                } catch (Throwable t) {
                    thrown.set(t);
                }
                flag.set(Thread.currentThread().isInterrupted());
            });
            waiter.start();
            AsyncJavaProgram program = null;
            while (program == null) {
                Thread.sleep(20);
                program = AsyncJavaProgram.find(pluginContext, "default");
            }
            waiter.interrupt();
            waiter.join();

            assertTrue(thrown.get() instanceof MojoExecutionException, String.valueOf(thrown.get()));
            assertTrue(flag.get(), "the interrupt flag is set again");
            assertFalse(program.isAlive(), "the joins waited for the program's threads");
            assertFalse(isListening(port));
            assertTrue(AsyncJavaProgram.findAll(pluginContext).isEmpty());
        });
    }

    @Test
    @InjectMojo(goal = "java")
    void stoppingProgramsRestoresOnlyTheirOwnSystemProperties(ExecJavaMojo first) throws Exception {
        for (boolean firstStopsFirst : new boolean[] {true, false}) {
            assertTimeoutPreemptively(LIMIT, () -> {
                pluginContext.clear();
                AsyncJavaProgram a = startWithProperty(first, "exec.async.a");
                AsyncJavaProgram b = startWithProperty(first, "exec.async.b");
                System.setProperty("exec.async.unrelated", "kept");
                try {
                    assertEquals("set", System.getProperty("exec.async.a"));
                    assertEquals("set", System.getProperty("exec.async.b"));

                    stop((firstStopsFirst ? a : b).getExecutionId());
                    assertNull(System.getProperty(firstStopsFirst ? "exec.async.a" : "exec.async.b"));
                    assertEquals("set", System.getProperty(firstStopsFirst ? "exec.async.b" : "exec.async.a"));

                    stop((firstStopsFirst ? b : a).getExecutionId());
                    assertNull(System.getProperty("exec.async.a"));
                    assertNull(System.getProperty("exec.async.b"));
                    assertEquals("kept", System.getProperty("exec.async.unrelated"));
                } finally {
                    System.clearProperty("exec.async.unrelated");
                }
            });
        }
    }

    /** Starts a program under its own execution id: the mojo gets a fresh id by a fresh mojoExecution. */
    @Test
    @InjectMojo(goal = "java")
    void programsSettingTheSameKeyRestoreItInAnyStopOrder(ExecJavaMojo first) throws Exception {
        for (boolean firstStopsFirst : new boolean[] {true, false}) {
            assertTimeoutPreemptively(LIMIT, () -> {
                pluginContext.clear();
                System.setProperty("exec.async.shared", "before");
                try {
                    AsyncJavaProgram a = startWithProperty(first, "exec.async.first", "exec.async.shared", "A");
                    AsyncJavaProgram b = startWithProperty(first, "exec.async.second", "exec.async.shared", "B");
                    assertEquals("B", System.getProperty("exec.async.shared"));

                    stop((firstStopsFirst ? a : b).getExecutionId());
                    assertEquals(firstStopsFirst ? "B" : "A", System.getProperty("exec.async.shared"));

                    stop((firstStopsFirst ? b : a).getExecutionId());
                    assertEquals("before", System.getProperty("exec.async.shared"));
                } finally {
                    System.clearProperty("exec.async.shared");
                }
            });
        }
    }

    private AsyncJavaProgram startWithProperty(ExecJavaMojo mojo, String key) throws Exception {
        return startWithProperty(mojo, key, key, "set");
    }

    private AsyncJavaProgram startWithProperty(ExecJavaMojo mojo, String executionId, String key, String value)
            throws Exception {
        asyncProgram(mojo, "org.codehaus.mojo.exec.AsyncTestMain", null, "wait");
        Property property = new Property();
        property.setKey(key);
        property.setValue(value);
        setVariableValueToObject(mojo, "systemProperties", new AbstractProperty[] {property});
        org.apache.maven.plugin.MojoExecution execution = new org.apache.maven.plugin.MojoExecution(null, executionId);
        setVariableValueToObject(mojo, "mojoExecution", execution);
        mojo.execute();
        return AsyncJavaProgram.find(pluginContext, executionId);
    }
}
