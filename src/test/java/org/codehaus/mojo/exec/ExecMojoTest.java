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
import org.mockito.Mock;

import static java.util.Collections.emptyMap;

/**
 * @author Jerome Lacoste
 * @version $Id$
 */
public class ExecMojoTest extends AbstractMojoTestCase {
    @Mock
    private MavenSession session;

    private static final File LOCAL_REPO = new File("src/test/repository");

    /*
     * Finding a file actually on disk of the test system makes some of the tests fail.
     * Hence a purely random UUID is used to prevent this situation.
     */
    private static final String SOME_EXECUTABLE = UUID.randomUUID().toString();

    private MockExecMojo mojo;

    static class MockExecMojo extends ExecMojo {
        public int executeResult;

        public List<CommandLine> commandLines = new ArrayList<CommandLine>();

        public String failureMsg;

        public Map<String, String> systemProperties = new HashMap<String, String>();

        protected int executeCommandLine(
                Executor exec, CommandLine commandLine, Map enviro, OutputStream out, OutputStream err)
                throws IOException, ExecuteException {
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
        mojo.setBasedir(File.createTempFile("mvn-temp", "txt").getParentFile());
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
        Map<String, String> enviro = new HashMap<String, String>();

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

            realMojo.setExecutable("javax.bat");
            cmd = realMojo.getExecutablePath(enviro, workdir);
            assertTrue(
                    "is bat file on windows, execute using ComSpec.",
                    cmd.getExecutable().equals(comSpec));

            enviro.put("PATH", workdir.getAbsolutePath() + File.separator + "target");
            cmd = realMojo.getExecutablePath(enviro, workdir);
            assertTrue(
                    "is bat file on windows' PATH, execute using ComSpec.",
                    cmd.getExecutable().equals(comSpec));
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

        final Map<String, String> enviro = new HashMap<String, String>();

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
}
