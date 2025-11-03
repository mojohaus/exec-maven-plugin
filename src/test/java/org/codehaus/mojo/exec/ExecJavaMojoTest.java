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
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Properties;

import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

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
        when(session.getCurrentProject()).thenReturn(project);

        Build build = new Build();
        build.setOutputDirectory(new File("target/test-classes").getAbsolutePath());
        when(project.getBuild()).thenReturn(build);
    }

    /*
     * This one won't work yet public void xxtestSimpleRunPropertiesAndArguments() throws MojoExecutionException,
     * Exception { File pom = new File( getBasedir(), "src/test/projects/project2/pom.xml" ); String output = execute(
     * pom, "java" ); System.out.println(output); assertEquals( -1, output.trim().indexOf( "ERROR" ) ); }
     */

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

    /**
     * MEXEC-29 exec:java throws NPE if the mainClass main method has not a correct signature
     */
    // Moved this test to src/it/mexec-29 (integration test)
    // cause it will fail. This is based of trying to
    // using dependencies (commons-logging:1.0.4:jar; commons-io:commons-is:1.1) which will be resolved
    // against maven central which will not work always in particular
    // when maven central is proxied via a repository manager.
    // This could be solved by using the local settings.xml
    // file of the user, but the tests don't use it.
    // Apart from that if the class does not exist as in this test
    // the real execution of the plugin will result in a ClassNotFoundException
    // like this:
    // [DEBUG] Adding project dependency artifact: commons-io to classpath
    // [DEBUG] Adding project dependency artifact: commons-logging to classpath
    // [DEBUG] joining on thread Thread[org.codehaus.mojo.exec.NoMain.main(),5,org.codehaus.mojo.exec.NoMain]
    // [WARNING]
    // java.lang.ClassNotFoundException: org.codehaus.mojo.exec.NoMain
    // at java.net.URLClassLoader$1.run(URLClassLoader.java:366)
    // at java.net.URLClassLoader$1.run(URLClassLoader.java:355)
    // at java.security.AccessController.doPrivileged(Native Method)
    // at java.net.URLClassLoader.findClass(URLClassLoader.java:354)
    // at java.lang.ClassLoader.loadClass(ClassLoader.java:423)
    // at java.lang.ClassLoader.loadClass(ClassLoader.java:356)
    // at org.codehaus.mojo.exec.ExecJavaMojo$1.run(ExecJavaMojo.java:293)
    // at java.lang.Thread.run(Thread.java:722)

    /*
     * public void testIncorrectMainMethodSignature() throws Exception { File pom = new File( getBasedir(),
     * "src/test/projects/project12/pom.xml" ); try { String output = execute( pom, "java" ); } catch
     * (MojoExecutionException e) { assertTrue( stringContains( e.getMessage(),
     * "The specified mainClass doesn't contain a main method with appropriate signature." ) ); } }
     */

    // this test doesn't work as the classpath passed to the project when executing the POM isn't the same as when maven
    // is executed from within the project dir
    // Should be moved as an integration-test
    /*
     * public void testSetClasspathScopeToTest() throws Exception { File pom = new File( getBasedir(),
     * "src/test/projects/project14/pom.xml" ); String output = execute( pom, "java" ); assertEquals( "Hello" +
     * System.getProperty( "line.separator" ), output ); }
     */

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
     * See <a href="http://jira.codehaus.org/browse/MEXEC-17">MEXEC-17</a>.
     */
    /**
     * This test doesn't work because the sun.tools.javac.Main class referenced in the project pom is found even if the
     * system scope dependencies are not added by the plugin. The class was probably loaded by another plugin ?! To fix
     * the test we have to: - maybe use a different system scope dependency/class to load. - find a different way to
     * test. When ran manually, the test works though (i.e. removing the code that manually adds the system scope
     * dependencies make the test fail). public void testSystemDependencyFound() throws Exception { File pom = new File(
     * getBasedir(), "src/test/projects/project11/pom.xml" ); String output = execute( pom, "java" ); assertEquals(
     * FindClassInClasspath.FOUND_ALL, output.trim() ); }
     */

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
}
