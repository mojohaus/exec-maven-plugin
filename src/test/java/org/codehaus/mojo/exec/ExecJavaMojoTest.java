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
import javax.inject.Inject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.apache.maven.api.plugin.testing.MojoExtension.setVariableValueToObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Jerome Lacoste
 * @version $Id$
 */
@ExtendWith(MockitoExtension.class)
@MojoTest
class ExecJavaMojoTest {

    @Inject
    private MavenSession session;

    @Inject
    private MavenProject project;

    @BeforeEach
    void beforeEach() {
        session.getSystemProperties().setProperty("test.version", "junit");
    }

    /**
     * Check that a simple execution with no arguments and no system properties produces the expected result.<br>
     * We load the config from a pom file and fill up the MavenProject property ourselves
     *
     * @throws Exception if any exception occurs
     */
    @Test
    @InjectMojo(goal = "java")
    @MojoParameter(name = "mainClass", value = "org.codehaus.mojo.exec.HelloRunnable")
    void runnable(ExecJavaMojo mojo) throws Exception {
        doExecute(mojo, null, null);
        assertEquals("junit: true", session.getSystemProperties().getProperty("hello.runnable.output"));
    }

    /**
     * Check that a simple execution with no arguments and no system properties produces the expected result.<br>
     * We load the config from a pom file and fill up the MavenProject property ourselves
     *
     * @throws Exception if any exception occurs
     */
    @Test
    @InjectMojo(goal = "java")
    @MojoParameter(name = "mainClass", value = "org.codehaus.mojo.exec.DummyMain")
    void simpleRun(ExecJavaMojo mojo) throws Exception {
        String output = execute(mojo);
        assertEquals("Hello" + System.lineSeparator(), output);
    }

    @Test
    @InjectMojo(goal = "java")
    @MojoParameter(name = "mainClass", value = "org.codehaus.mojo.exec.JSR512DummyMain1")
    void jsr512InstanceMainNoArgs(ExecJavaMojo mojo) throws Exception {
        String output = execute(mojo);
        assertEquals("Correct choice" + System.lineSeparator(), output);
    }

    @Test
    @InjectMojo(goal = "java")
    @MojoParameter(name = "mainClass", value = "org.codehaus.mojo.exec.JSR512DummyMain2")
    @MojoParameter(name = "arguments", value = "arg1,arg2")
    void jsr512PrefersStringArrayArgs(ExecJavaMojo mojo) throws Exception {
        String output = execute(mojo);
        assertEquals("Correct choice arg1 arg2" + System.lineSeparator(), output);
    }

    @Test
    @InjectMojo(goal = "java")
    @MojoParameter(name = "mainClass", value = "org.codehaus.mojo.exec.JSR512DummyMain3")
    void jsr512StaticMainNoArgs(ExecJavaMojo mojo) throws Exception {
        String output = execute(mojo);
        assertEquals("Correct choice" + System.lineSeparator(), output);
    }

    @Test
    @InjectMojo(goal = "java")
    @MojoParameter(name = "mainClass", value = "org.codehaus.mojo.exec.JSR512DummyMain6")
    void jsr512PackagePrivateStaticMainNoArgs(ExecJavaMojo mojo) throws Exception {
        String output = execute(mojo);
        assertEquals("Correct choice" + System.lineSeparator(), output);
    }

    @Test
    @InjectMojo(goal = "java")
    @MojoParameter(name = "mainClass", value = "org.codehaus.mojo.exec.JSR512DummyMain7")
    @MojoParameter(name = "arguments", value = "arg1,arg2")
    void jsr512PackagePrivateStaticMainWithArgs(ExecJavaMojo mojo) throws Exception {
        String output = execute(mojo);
        assertEquals("Correct choice arg1 arg2" + System.lineSeparator(), output);
    }

    @Test
    @InjectMojo(goal = "java")
    @MojoParameter(name = "mainClass", value = "org.codehaus.mojo.exec.JSR512DummyMain4")
    void jsr512FailureInstanceMainPrivateNoArgsConstructor(ExecJavaMojo mojo) {
        MojoExecutionException exception = assertThrows(MojoExecutionException.class, () -> execute(mojo));
        assertEquals(
                "The specified mainClass doesn't contain a main method with appropriate signature.",
                exception.getCause().getMessage());
    }

    @Test
    @InjectMojo(goal = "java")
    @MojoParameter(name = "mainClass", value = "org.codehaus.mojo.exec.JSR512DummyMain5")
    @MojoParameter(name = "arguments", value = "arg1,arg2")
    void jsr512InheritedMain(ExecJavaMojo mojo) throws Exception {
        String output = execute(mojo);
        assertEquals("Correct choice arg1 arg2" + System.lineSeparator(), output);
    }

    /**
     * MEXEC-10 Check that an execution with no arguments and an system property with no value produces the expected
     * result<br>
     * We load the config from a pom file and fill up the MavenProject property ourselves
     *
     * @throws Exception if any exception occurs
     */
    @Test
    @InjectMojo(goal = "java", pom = "src/test/projects/project5/pom.xml")
    void emptySystemProperty(ExecJavaMojo mojo) throws Exception {

        assertNull(System.getProperty("test.name"), "System property not yet created");

        assertEquals("Hello " + System.lineSeparator(), execute(mojo));

        // ensure we get back in the original state and didn't leak the execution config
        assertNull(System.getProperty("test.name"), "System property not yet created");
    }

    /**
     * Check that an execution that throws propagates the cause of the failure into the output
     * and correctly unwraps the InvocationTargetException.
     *
     * @author Lukasz Cwik
     */
    @Test
    @InjectMojo(goal = "java")
    @MojoParameter(name = "mainClass", value = "org.codehaus.mojo.exec.ThrowingMain")
    void runWhichThrowsExceptionIsNotWrappedInInvocationTargetException(ExecJavaMojo mojo) {
        MojoExecutionException exception = assertThrows(MojoExecutionException.class, () -> execute(mojo));
        assertInstanceOf(IOException.class, exception.getCause());
        assertEquals("expected IOException thrown by test", exception.getCause().getMessage());
    }

    // GH-526
    @Test
    @InjectMojo(goal = "java")
    @MojoParameter(name = "mainClass", value = "org.codehaus.mojo.exec.SlowMain")
    void waitsForMainClassWhenRunFromDaemonThread(ExecJavaMojo mojo) throws Exception {
        System.clearProperty(SlowMain.DONE_PROPERTY);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread daemon = new Thread(() -> {
            try {
                execute(mojo);
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        daemon.setDaemon(true);
        try {
            daemon.start();
            daemon.join(30_000);

            assertNull(failure.get());
            assertEquals("true", System.getProperty(SlowMain.DONE_PROPERTY), "the main class ran to the end");
        } finally {
            System.clearProperty(SlowMain.DONE_PROPERTY);
        }
    }

    // GH-141
    @Test
    @InjectMojo(goal = "java")
    @MojoParameter(name = "mainClass", value = "org.codehaus.mojo.exec.ThreadHandlerMain")
    void uncaughtExceptionHandlerOfTheMainThreadIsCalled(ExecJavaMojo mojo) {
        System.getProperties().remove(ThreadHandlerMain.SEEN_PROPERTY);
        try {
            MojoExecutionException exception = assertThrows(MojoExecutionException.class, () -> execute(mojo));

            assertInstanceOf(IllegalStateException.class, exception.getCause());
            Object seen = System.getProperties().get(ThreadHandlerMain.SEEN_PROPERTY);
            assertInstanceOf(IllegalStateException.class, seen, "the handler set by the program was called");
        } finally {
            System.getProperties().remove(ThreadHandlerMain.SEEN_PROPERTY);
        }
    }

    // GH-141
    @Test
    @InjectMojo(goal = "java")
    @MojoParameter(name = "mainClass", value = "org.codehaus.mojo.exec.ThrowingMain")
    void defaultUncaughtExceptionHandlerIsCalled(ExecJavaMojo mojo) {
        Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
        AtomicReference<Throwable> seen = new AtomicReference<>();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> seen.set(throwable));
        try {
            MojoExecutionException exception = assertThrows(MojoExecutionException.class, () -> execute(mojo));

            assertInstanceOf(IOException.class, exception.getCause());
            assertEquals(exception.getCause(), seen.get(), "the default handler was called");
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original);
        }
    }

    /**
     * MEXEC-29 exec:java threw an NPE when the main method of the main class had no usable signature.
     */
    @Test
    @InjectMojo(goal = "java")
    @MojoParameter(name = "mainClass", value = "org.codehaus.mojo.exec.WrongSignatureMain")
    void incorrectMainMethodSignature(ExecJavaMojo mojo) {
        MojoExecutionException exception = assertThrows(MojoExecutionException.class, () -> execute(mojo));
        assertTrue(
                exception
                        .getCause()
                        .getMessage()
                        .contains("The specified mainClass doesn't contain a main method with appropriate signature."),
                String.valueOf(exception.getCause()));
    }

    /**
     * MEXEC-29: a main class that is not on the classpath fails with the class name in the message.
     */
    @Test
    @InjectMojo(goal = "java")
    @MojoParameter(name = "mainClass", value = "org.codehaus.mojo.exec.DoesNotExist")
    void missingMainClass(ExecJavaMojo mojo) {
        MojoExecutionException exception = assertThrows(MojoExecutionException.class, () -> execute(mojo));
        assertInstanceOf(ClassNotFoundException.class, exception.getCause());
        assertTrue(
                exception.getCause().getMessage().contains("org.codehaus.mojo.exec.DoesNotExist"),
                String.valueOf(exception.getCause()));
    }

    /**
     * For cases where the Java code spawns Threads and main returns soon. See
     * <a href="http://jira.codehaus.org/browse/MEXEC-6">MEXEC-6</a>.
     *
     * @throws Exception if any exception occurs
     */
    @Test
    @InjectMojo(goal = "java")
    @MojoParameter(name = "mainClass", value = "org.codehaus.mojo.exec.MainWithThreads")
    @MojoParameter(name = "arguments", value = "cancelTimer")
    void waitNoDaemonThreads(ExecJavaMojo mojo) throws Exception {
        String output = execute(mojo);
        assertEquals(MainWithThreads.ALL_EXITED, output.trim());
    }

    /**
     * For cases where the Java code spawns Threads and main returns soon, but code contains non interruptible threads.
     * User is required to timeout the execution, otherwise it will hang. See
     * <a href="http://jira.codehaus.org/browse/MEXEC-15">MEXEC-15</a>.
     *
     * @throws Exception if any exception occurs
     */
    @Test
    @InjectMojo(goal = "java")
    @MojoParameter(name = "mainClass", value = "org.codehaus.mojo.exec.MainWithThreads")
    @MojoParameter(name = "daemonThreadJoinTimeout", value = "4000")
    void waitNonInterruptibleDaemonThreads(ExecJavaMojo mojo) throws Exception {
        String output = execute(mojo);
        assertEquals(MainWithThreads.TIMER_IGNORED, output.trim());
    }

    /**
     * See <a href="http://jira.codehaus.org/browse/MEXEC-15">MEXEC-15</a>,
     * <a href="https://github.com/mojohaus/exec-maven-plugin/issues/391">GitHub-391</a>.
     * <p>
     * FIXME: This sometimes fails with {@code unit.framework.ComparisonFailure: expected:<...>; but was:<...3(f)>}.
     *
     * @throws Exception if any exception occurs
     */
    @Test
    @InjectMojo(goal = "java")
    @MojoParameter(name = "mainClass", value = "org.codehaus.mojo.exec.MainUncooperative")
    @MojoParameter(name = "daemonThreadJoinTimeout", value = "3000")
    @MojoParameter(name = "stopUnresponsiveDaemonThreads", value = "true")
    @EnabledForJreRange(max = JRE.JAVA_19)
    void uncooperativeThread(ExecJavaMojo mojo) throws Exception {
        String output = execute(mojo);
        // note: execute() will wait a little bit before returning the output,
        // thereby allowing the stop()'ed thread to output the final "(f)".
        assertEquals(MainUncooperative.SUCCESS, output.trim());
    }

    @Test
    @InjectMojo(goal = "java")
    @MojoParameter(name = "mainClass", value = "org.codehaus.mojo.exec.MainUncooperative")
    @MojoParameter(name = "daemonThreadJoinTimeout", value = "3000")
    @MojoParameter(name = "stopUnresponsiveDaemonThreads", value = "true")
    @EnabledForJreRange(min = JRE.JAVA_20)
    void uncooperativeThreadJdk20(ExecJavaMojo mojo) throws Exception {
        String output = execute(mojo);
        // note: execute() will wait a little bit before returning the output,
        // thereby allowing the stop()'ed thread to output the final "(f)".
        assertEquals(MainUncooperative.INTERRUPTED_BUT_NOT_STOPPED, output.trim());
    }

    /**
     * Test the commandline parsing facilities of the {@link AbstractExecMojo} class
     *
     * @throws Exception if any exception occurs
     */
    @Test
    @InjectMojo(goal = "java")
    @MojoParameter(name = "mainClass", value = "org.codehaus.mojo.exec.DummyMain")
    @MojoParameter(name = "commandlineArgs", value = "\"Arg1\" \"Arg2a Arg2b\"")
    void runWithArgs(ExecJavaMojo mojo) throws Exception {
        String resultString = execute(mojo);

        String LS = System.lineSeparator();
        String expectedResult = "Hello" + LS + "Arg1" + LS + "Arg2a Arg2b" + LS;
        assertEquals(expectedResult, resultString);
    }

    /**
     * Test the commandline parsing facilities of the {@link AbstractExecMojo} class
     *
     * @throws Exception if any exception occurs
     */
    @Test
    @InjectMojo(goal = "java")
    @MojoParameter(name = "mainClass", value = "CompactClass")
    void runCompact(ExecJavaMojo mojo) throws Exception {
        String resultString = execute(mojo);

        assertEquals("Hello World", resultString.trim());
    }

    /**
     * Ensures that classpath can be filtered (exclude from plugin deps or project deps) to resolve conflicts.
     *
     * @throws Exception if something unexpected occurs.
     */
    @Test
    @InjectMojo(goal = "java", pom = "src/test/projects/project16/pom.xml")
    void excludedClasspathElementSlf4jSimple(ExecJavaMojo mojo) throws Exception {
        String LS = System.lineSeparator();

        // slf4j-simple
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        execute(mojo, stdout, stderr);
        assertEquals("org.slf4j.impl.SimpleLogger", stdout.toString().trim());
        assertEquals(
                "[org.codehaus.mojo.exec.Slf4jMain.main()] INFO org.codehaus.mojo.exec.Slf4jMain - hello[]" + LS,
                stderr.toString());
    }

    @Test
    @InjectMojo(goal = "java", pom = "src/test/projects/project17/pom.xml")
    void excludedClasspathElementSlf4jJdk14(ExecJavaMojo mojo) throws Exception {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        execute(mojo, stdout, stderr);
        assertEquals("org.slf4j.impl.JDK14LoggerAdapter", stdout.toString().trim());
        final String stderrString = stderr.toString(); // simpler check, just validate it is not simple output
        assertTrue(stderrString.contains(" org.codehaus.mojo.exec.Slf4jMain main"));
        assertTrue(stderrString.contains(": hello[]"));
    }

    /**
     * Ensure all project properties can be forwarded to the execution as system properties.
     *
     * @throws Exception if any exception occurs
     */
    @Test
    @InjectMojo(goal = "java", pom = "src/test/projects/project18/pom.xml")
    void projectProperties(ExecJavaMojo mojo) throws Exception {
        // only mojo configuration is taken from the pom, we have to mock the project properties ourselves
        Properties properties = new Properties();
        properties.put("test.name", "project18 project");
        Mockito.when(project.getProperties()).thenReturn(properties);

        String output = execute(mojo);
        assertEquals("Hello project18 project" + System.lineSeparator(), output);
    }

    private Artifact jarWithMainClass(String mainClass) throws IOException {
        Path jar = Files.createTempFile("exec-main", ".jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (mainClass != null) {
            manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);
        }
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            out.flush();
        }
        Artifact artifact = new DefaultArtifact(
                "org.acme", "runner", "1.0", "runtime", "jar", null, new DefaultArtifactHandler("jar"));
        artifact.setFile(jar.toFile());
        return artifact;
    }

    private void useExecutableDependency(ExecJavaMojo mojo, Artifact artifact) throws Exception {
        ExecutableDependency dependency = new ExecutableDependency();
        dependency.setGroupId("org.acme");
        dependency.setArtifactId("runner");
        setVariableValueToObject(mojo, "executableDependency", dependency);
        setVariableValueToObject(mojo, "pluginDependencies", Collections.singletonList(artifact));
    }

    // https://github.com/mojohaus/exec-maven-plugin/issues/89
    @Test
    @InjectMojo(goal = "java")
    void mainClassComesFromTheManifestOfTheExecutableDependency(ExecJavaMojo mojo) throws Exception {
        useExecutableDependency(mojo, jarWithMainClass("org.codehaus.mojo.exec.DummyMain"));

        assertEquals("Hello" + System.lineSeparator(), execute(mojo));
    }

    // https://github.com/mojohaus/exec-maven-plugin/issues/89
    @Test
    @InjectMojo(goal = "java")
    @MojoParameter(name = "mainClass", value = "org.codehaus.mojo.exec.JSR512DummyMain1")
    void configuredMainClassWinsOverTheManifest(ExecJavaMojo mojo) throws Exception {
        useExecutableDependency(mojo, jarWithMainClass("org.codehaus.mojo.exec.DummyMain"));

        assertEquals("Correct choice" + System.lineSeparator(), execute(mojo));
    }

    // https://github.com/mojohaus/exec-maven-plugin/issues/89
    @Test
    @InjectMojo(goal = "java")
    void missingMainClassEntryFailsWithAClearMessage(ExecJavaMojo mojo) throws Exception {
        useExecutableDependency(mojo, jarWithMainClass(null));

        MojoExecutionException exception = assertThrows(MojoExecutionException.class, () -> execute(mojo));
        assertTrue(exception.getMessage().contains("Main-Class"), exception.getMessage());
        assertTrue(exception.getMessage().contains("org.acme:runner"), exception.getMessage());
    }

    @Test
    @InjectMojo(goal = "java")
    void mainClassIsStillRequiredWithoutExecutableDependency(ExecJavaMojo mojo) {
        MojoExecutionException exception = assertThrows(MojoExecutionException.class, () -> execute(mojo));
        assertTrue(exception.getMessage().contains("mainClass"), exception.getMessage());
    }

    // https://github.com/mojohaus/exec-maven-plugin/issues/513
    @Test
    @InjectMojo(goal = "java")
    @MojoParameter(name = "mainClass", value = "org.codehaus.mojo.exec.LoaderProbeMain")
    void classLoaderIsClosedByDefault(ExecJavaMojo mojo) throws Exception {
        assertNull(runProbeAndFindResource(mojo));
    }

    // https://github.com/mojohaus/exec-maven-plugin/issues/513
    @Test
    @InjectMojo(goal = "java")
    @MojoParameter(name = "mainClass", value = "org.codehaus.mojo.exec.LoaderProbeMain")
    @MojoParameter(name = "keepClassLoaderOpen", value = "true")
    void classLoaderStaysOpenWhenAsked(ExecJavaMojo mojo) throws Exception {
        assertNotNull(runProbeAndFindResource(mojo));
    }

    /**
     * Runs the probe with a directory holding a marker file on the loader's own URLs, then looks the marker up.
     */
    private URL runProbeAndFindResource(ExecJavaMojo mojo) throws Exception {
        Path dir = Files.createTempDirectory("exec-probe");
        Files.createFile(dir.resolve("marker.txt"));
        setVariableValueToObject(mojo, "additionalClasspathElements", Collections.singletonList(dir.toString()));
        LoaderProbeMain.loader = null;

        execute(mojo);

        assertNotNull(LoaderProbeMain.loader);
        return ((URLClassLoader) LoaderProbeMain.loader).findResource("marker.txt");
    }

    /**
     * @return output from System.out during mojo execution
     */
    private String execute(ExecJavaMojo mojo) throws Exception {
        return execute(mojo, new ByteArrayOutputStream(), new ByteArrayOutputStream());
    }

    /**
     * @return output from System.out during mojo execution
     */
    private String execute(ExecJavaMojo mojo, ByteArrayOutputStream stringOutputStream, OutputStream stderr)
            throws Exception {

        assertNotNull(mojo);

        // trap System.out
        PrintStream out = System.out;
        PrintStream err = System.err;
        System.setOut(new PrintStream(stringOutputStream));
        System.setErr(new PrintStream(stderr));
        // ensure we don't log unnecessary stuff which would interfere with assessing success of tests
        // mojo.setLog(new DefaultLog(new ConsoleLogger(Logger.LEVEL_ERROR, "exec:java")));

        doExecute(mojo, out, err);

        return stringOutputStream.toString();
    }

    private void doExecute(final ExecJavaMojo mojo, final PrintStream out, final PrintStream err)
            throws MojoExecutionException, MojoFailureException, InterruptedException {
        try {
            mojo.execute();
        } finally {
            // see testUncooperativeThread() for explaination
            Thread.sleep(300); // time seems about right
            if (out != null) {
                System.setOut(out);
            }
            if (err != null) {
                System.setErr(err);
            }
        }
    }

    /**
     * Test the async feature.
     *
     * @throws Exception if any exception occurs
     */
    @Test
    @InjectMojo(goal = "java", pom = "src/test/projects/project-async/pom.xml")
    void asyncRun(ExecJavaMojo mojo) throws Exception {
        String output = execute(mojo);
        assertTrue(output.trim().contains("Started test server."));
        TestServerMain.stop();
    }
}
