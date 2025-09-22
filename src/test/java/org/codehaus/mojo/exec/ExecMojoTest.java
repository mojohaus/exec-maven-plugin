package org.codehaus.mojo.exec;

/*
 * Copyright 2005 The Codehaus. Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.OS;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.toolchain.ToolchainManager;

import static java.util.Collections.emptyMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Jerome Lacoste
 * @version $Id$
 */
public class ExecMojoTest extends AbstractMojoTestCase {
    private MavenSession session = mock(MavenSession.class);
    private ToolchainManager toolchainManager = mock(ToolchainManager.class);

    private static final File LOCAL_REPO = new File("src/test/repository");

    /*
     * Finding a file actually on disk of the test system makes some of the tests fail.
     * Hence a purely random UUID is used to prevent this situation.
     */
    private static final String SOME_EXECUTABLE = UUID.randomUUID().toString();

    private MockExecMojo mojo;

    static class MockExecMojo extends ExecMojo {
        public int executeResult;

        public List<CommandLine> commandLines = new ArrayList<>();

        public String failureMsg;

        public Map<String, String> systemProperties = new HashMap<>();

        protected int executeCommandLine(
                Executor exec, CommandLine commandLine, Map enviro, OutputStream out, OutputStream err)
                throws ExecuteException {
            commandLines.add(commandLine);
            if (failureMsg != null) {
                throw new ExecuteException(failureMsg, executeResult);
            }
            return executeResult;
        }

        protected String getSystemProperty(String key) {
            return systemProperties.get(key);
        }

        int getAmountExecutedCommandLines() {
            return commandLines.size();
        }

        CommandLine getExecutedCommandline(int index) {
            return commandLines.get(index);
        }
    }

    public void setUp() throws Exception {
        super.setUp();
        mojo = new MockExecMojo();
        // note: most of the tests below assume that the specified
        // executable path is not fully specicied. See ExecMojo#getExecutablePath
        mojo.setExecutable(SOME_EXECUTABLE);
        mojo.setArguments(Arrays.asList(new String[] {"--version"}));
        mojo.executeResult = 0;
        mojo.setBasedir(Files.createTempFile("mvn-temp", "txt").getParent().toFile());
    }

    public void testRunOK() throws MojoExecutionException {
        mojo.execute();

        checkMojo(SOME_EXECUTABLE + " --version");
    }

    /*
     * This one won't work yet public void xxtestSimpleRunPropertiesAndArguments() throws MojoExecutionException,
     * Exception { File pom = new File( getBasedir(), "src/test/projects/project1/pom.xml" ); String output = execute(
     * pom, "exec" ); System.out.println(" OUTPUT" + output + "\n\n\n"); String expectedOutput =
     * "arg.arg1\narg.arg2\nproject.env1=value1"; // FIXME should work on Windows as well assertEquals( expectedOutput,
     * output ); }
     */

    /*
     * integration test... - compile the Test class using mvn clean compile - run the test file using java, use it to
     * generate a file whose contains are compared to expected output
     */

    // MEXEC-12, MEXEC-72
    public void testGetExecutablePath() throws IOException {

        ExecMojo realMojo = new ExecMojo();

        File workdir = new File(System.getProperty("user.dir"));
        Map<String, String> enviro = new HashMap<>();

        String myJavaPath = "target" + File.separator + "javax";
        File f = new File(myJavaPath);
        assertTrue("file created...", f.createNewFile());
        assertTrue("file exists...", f.exists());

        realMojo.setExecutable(myJavaPath);

        CommandLine cmd = realMojo.getExecutablePath(enviro, workdir);
        assertTrue("File exists so path is absolute", cmd.getExecutable().startsWith(System.getProperty("user.dir")));

        f.delete();
        assertFalse("file deleted...", f.exists());
        cmd = realMojo.getExecutablePath(enviro, workdir);
        assertEquals("File doesn't exist. Let the system find it (in that PATH?)", myJavaPath, cmd.getExecutable());

        if (OS.isFamilyWindows()) // Exec Maven Plugin only supports Batch detection and PATH search on Windows
        {
            myJavaPath = "target" + File.separator + "javax.bat";
            f = new File(myJavaPath);
            assertTrue("file created...", f.createNewFile());
            assertTrue("file exists...", f.exists());

            final String comSpec = System.getenv("ComSpec");

            // for windows scripts cmd should be used
            realMojo.setExecutable("javax.bat");
            cmd = realMojo.getExecutablePath(enviro, workdir);
            assertEquals("is bat file on windows, execute using ComSpec.", comSpec, cmd.getExecutable());
            assertEquals("is /c argument pass using ComSpec.", "/c", cmd.getArguments()[0]);

            String path = workdir.getAbsolutePath() + File.separator + "target";
            enviro.put("Path", path);
            realMojo.setExecutable("javax"); // FIXME javax.bat should be also allowed
            cmd = realMojo.getExecutablePath(enviro, workdir);
            assertEquals("is bat file on windows, execute using ComSpec.", comSpec, cmd.getExecutable());
            assertEquals("is /c argument pass using ComSpec.", "/c", cmd.getArguments()[0]);
            assertEquals("full path is discovered.", path + File.separator + "javax.bat", cmd.getArguments()[1]);

            f.delete();
            assertFalse("file deleted...", f.exists());
        }
    }

    // under Windows cmd/bat files should be preferred over files with the same prefix without extension, e.g.
    // if there are "node" and "node.cmd" the mojo should pick "node.cmd"
    // see https://github.com/mojohaus/exec-maven-plugin/issues/42
    public void testGetExecutablePathPreferExecutableExtensionsOnWindows() throws IOException {
        // this test is for Windows
        if (!OS.isFamilyWindows()) {
            return;
        }
        final ExecMojo realMojo = new ExecMojo();

        final String tmp = System.getProperty("java.io.tmpdir");
        final File workdir = new File(tmp, "testGetExecutablePathPreferExecutableExtensionsOnWindows");
        workdir.mkdirs();

        final Map<String, String> enviro = new HashMap<>();

        final File f = new File(workdir, "mycmd");
        final File fCmd = new File(workdir, "mycmd.cmd");
        f.createNewFile();
        fCmd.createNewFile();
        assertTrue("file exists...", f.exists());
        assertTrue("file exists...", fCmd.exists());

        realMojo.setExecutable("mycmd");

        final CommandLine cmd = realMojo.getExecutablePath(enviro, workdir);
        // cmdline argumets are: [/c, %path-to-temp%\mycmd.cmd], so check second argument
        assertTrue("File should have cmd extension", cmd.getArguments()[1].endsWith("mycmd.cmd"));

        f.delete();
        fCmd.delete();
        assertFalse("file deleted...", f.exists());
        assertFalse("file deleted...", fCmd.exists());
    }

    public void testRunFailure() {
        mojo.executeResult = 1;

        try {
            mojo.execute();
            fail("expected failure");
        } catch (MojoExecutionException e) {
            assertEquals("Result of " + mojo.getExecutedCommandline(0) + " execution is: '1'.", e.getMessage());
        }

        checkMojo(SOME_EXECUTABLE + " --version");
    }

    public void testRunError() {
        mojo.failureMsg = "simulated failure";

        try {
            mojo.execute();
            fail("expected failure");
        } catch (MojoExecutionException e) {
            assertEquals("Command execution failed.", e.getMessage());
        }

        checkMojo(SOME_EXECUTABLE + " --version");
    }

    public void testOverrides() throws MojoExecutionException {
        mojo.systemProperties.put("exec.args", "-f pom.xml");
        mojo.execute();

        checkMojo(SOME_EXECUTABLE + " -f pom.xml");
    }

    public void testOverrides3() throws MojoExecutionException {
        mojo.systemProperties.put("exec.args", null);
        mojo.execute();

        checkMojo(SOME_EXECUTABLE + " --version");

        mojo.commandLines.clear();
        mojo.systemProperties.put("exec.args", "");
        mojo.execute();

        checkMojo(SOME_EXECUTABLE + " --version");
    }

    public void testIsResultCodeAFailure() {
        ExecMojo execMojo = new ExecMojo();
        assertTrue(execMojo.isResultCodeAFailure(1));
        assertFalse(execMojo.isResultCodeAFailure(0));

        execMojo.setSuccessCodes(new Integer[0]);
        assertTrue(execMojo.isResultCodeAFailure(1));
        assertFalse(execMojo.isResultCodeAFailure(0));

        execMojo.setSuccessCodes(new Integer[] {Integer.valueOf("2"), Integer.valueOf("5")});
        assertTrue(execMojo.isResultCodeAFailure(0));
        assertTrue(execMojo.isResultCodeAFailure(10));
        assertFalse(execMojo.isResultCodeAFailure(2));
        assertFalse(execMojo.isResultCodeAFailure(5));
    }

    // MEXEC-81
    public void testParseCommandlineOSWin() throws Exception {
        ExecMojo execMojo = new ExecMojo();
        final String javaHome = "C:\\Java\\jdk1.5.0_15";
        // can only be set by expression or plugin-configuration
        setVariableValueToObject(execMojo, "commandlineArgs", javaHome);
        String[] args = execMojo.parseCommandlineArgs();
        assertEquals(javaHome, args[0]);
    }

    public void test_exec_receives_all_parameters() throws MojoExecutionException {
        // given
        ExecMojo execMojo = new ExecMojo();
        execMojo.setExecutable("mkdir");
        execMojo.setArguments(Arrays.asList("-p", "dist/mails"));
        execMojo.setBasedir(new File("target"));

        // when
        final CommandLine commandLine = execMojo.getExecutablePath(emptyMap(), new File("target"));
        execMojo.execute();

        // then
        assertTrue(
                "dir should have been created",
                Paths.get("target", "dist", "mails").toFile().exists());
    }

    public void testToolchainJavaHomePropertySetWhenToolchainIsUsed() throws Exception {
        // given
        File basedir;
        String testJavaPath;
        File pom;

        if (OS.isFamilyWindows()) {
            testJavaPath = "\\path\\to\\java\\home";
            pom = new File(getBasedir(), "src\\test\\projects\\project21\\pom.xml");
            when(toolchainManager.getToolchainFromBuildContext(any(), eq(session)))
                    .thenReturn(new DummyJdkToolchain(testJavaPath + "\\bin\\java"));
        } else {
            testJavaPath = "/path/to/java/home";
            pom = new File(getBasedir(), "src/test/projects/project20/pom.xml");
            when(toolchainManager.getToolchainFromBuildContext(any(), eq(session)))
                    .thenReturn(new DummyJdkToolchain(testJavaPath + "/bin/java"));
        }

        ExecMojo execMojo = (ExecMojo) lookupMojo("exec", pom);
        setVariableValueToObject(execMojo, "session", session);
        setVariableValueToObject(execMojo, "toolchainManager", toolchainManager);

        basedir = new File("target");
        execMojo.setBasedir(basedir);

        // when
        execMojo.execute();

        // then
        Path resultFilePath = basedir.toPath().resolve("testfile.txt");
        String result = new String(Files.readAllBytes(resultFilePath), StandardCharsets.UTF_8);
        assertTrue(result.contains(testJavaPath));
    }

    private void checkMojo(String expectedCommandLine) {
        assertEquals(1, mojo.getAmountExecutedCommandLines());
        CommandLine commandline = mojo.getExecutedCommandline(0);
        // do NOT depend on Commandline toString()
        assertEquals(expectedCommandLine, getCommandLineAsString(commandline));
    }

    private String getCommandLineAsString(CommandLine commandline) {
        // for the sake of the test comparisons, cut out the eventual
        // cmd /c *.bat conversion
        String result = commandline.getExecutable();
        boolean isCmd = false;
        if (OS.isFamilyWindows() && result.equals("cmd")) {
            result = "";
            isCmd = true;
        }
        String[] arguments = commandline.getArguments();
        for (int i = 0; i < arguments.length; i++) {
            String arg = arguments[i];
            if (isCmd && i == 0 && "/c".equals(arg)) {
                continue;
            }
            if (isCmd && i == 1 && arg.endsWith(".bat")) {
                arg = arg.substring(0, arg.length() - ".bat".length());
            }
            result += (result.length() == 0 ? "" : " ") + arg;
        }
        return result;
    }

    public void testGetShebang() throws Exception {
        ExecMojo execMojo = new ExecMojo();
        Method method = ExecMojo.class.getDeclaredMethod("getShebang", File.class);
        method.setAccessible(true);

        // without shebang
        File noShebang = Files.createTempFile("noShebang", ".sh").toFile();
        Files.write(noShebang.toPath(), Arrays.asList("echo hello"), StandardCharsets.UTF_8);
        assertNull(method.invoke(execMojo, noShebang));
        noShebang.delete();

        // with shebang
        File withShebang = Files.createTempFile("withShebang", ".sh").toFile();
        Files.write(withShebang.toPath(), Arrays.asList("#!/bin/bash -x", "echo hello"), StandardCharsets.UTF_8);
        assertEquals("/bin/bash -x", method.invoke(execMojo, withShebang));
        withShebang.delete();

        // with shebang but no args
        File withShebangNoArgs =
                Files.createTempFile("withShebangNoArgs", ".sh").toFile();
        Files.write(
                withShebangNoArgs.toPath(),
                Arrays.asList("#!/usr/bin/env python3", "print('hello')"),
                StandardCharsets.UTF_8);
        assertEquals("/usr/bin/env python3", method.invoke(execMojo, withShebangNoArgs));
        withShebangNoArgs.delete();
    }

    public void testCreateEnvWrapperFile() throws Exception {
        ExecMojo execMojo = new ExecMojo();

        // without shebang
        File envScript = Files.createTempFile("envScript", ".sh").toFile();
        Files.write(envScript.toPath(), Arrays.asList("export TEST_VAR=test_value"), StandardCharsets.UTF_8);
        File wrapper = execMojo.createEnvWrapperFile(envScript);
        List<String> lines = Files.readAllLines(wrapper.toPath(), StandardCharsets.UTF_8);

        if (OS.isFamilyWindows()) {
            assertEquals("@echo off", lines.get(0));
            assertTrue(lines.get(1).startsWith("call \""));
            assertTrue(lines.get(1).endsWith(envScript.getCanonicalPath() + "\""));
            assertEquals("echo " + EnvStreamConsumer.START_PARSING_INDICATOR, lines.get(2));
            assertEquals("set", lines.get(3));
        } else {
            assertEquals("#!/bin/sh", lines.get(0));
            assertEquals(". " + envScript.getCanonicalPath(), lines.get(1));
            assertEquals("echo " + EnvStreamConsumer.START_PARSING_INDICATOR, lines.get(2));
            assertEquals("env", lines.get(3));
        }
        wrapper.delete();
        envScript.delete();

        // with shebang
        File envScriptWithShebang =
                Files.createTempFile("envScriptWithShebang", ".sh").toFile();
        Files.write(
                envScriptWithShebang.toPath(),
                Arrays.asList("#!/bin/bash", "export TEST_VAR=test_value"),
                StandardCharsets.UTF_8);
        File wrapper2 = execMojo.createEnvWrapperFile(envScriptWithShebang);
        List<String> lines2 = Files.readAllLines(wrapper2.toPath(), StandardCharsets.UTF_8);

        if (OS.isFamilyWindows()) {
            assertEquals("@echo off", lines2.get(0));
            assertTrue(lines2.get(1).startsWith("call \""));
            assertTrue(lines2.get(1).endsWith(envScriptWithShebang.getCanonicalPath() + "\""));
            assertEquals("echo " + EnvStreamConsumer.START_PARSING_INDICATOR, lines2.get(2));
            assertEquals("set", lines2.get(3));
        } else {
            assertEquals("#!/bin/bash", lines2.get(0));
            assertEquals(". " + envScriptWithShebang.getCanonicalPath(), lines2.get(1));
            assertEquals("echo " + EnvStreamConsumer.START_PARSING_INDICATOR, lines2.get(2));
            assertEquals("env", lines2.get(3));
        }
        wrapper2.delete();
        envScriptWithShebang.delete();
    }
}
