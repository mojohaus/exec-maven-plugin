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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Stops the Java programs that <code>exec:java</code> runs in the background (<code>async</code>) in the current
 * project: interrupts their threads, waits for them, closes their class loaders and restores the system properties.
 * The goal fails if a program threw an exception, so that a failure in the background fails the build. It also
 * fails when <code>failOnLingeringThreads</code> is set and a thread of the program is still alive after the
 * interruption and the wait.
 * <p>
 * Without a program to stop it logs that and succeeds, even when <code>startExecutionId</code> names an execution
 * that has none, so it is safe to bind when the start is skipped or does not run in the background.
 * </p>
 *
 * @since 3.8.0
 */
@Mojo(name = "java-stop", threadSafe = true, defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST)
public class ExecJavaStopMojo extends AbstractMojo {

    /**
     * The id of the <code>exec:java</code> execution to stop. Without it every program that runs in the background in
     * this project is stopped, the latest started first. An id without a program is not an error. On the command line the id of an execution is
     * <code>default-cli</code>.
     *
     * @since 3.8.0
     */
    @Parameter(property = "exec.startExecutionId")
    private String startExecutionId;

    /**
     * Skip the execution.
     *
     * @since 3.8.0
     */
    @Parameter(property = "exec.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("skipping execute as per configuration");
            return;
        }

        Map<String, Object> context = getPluginContext();
        if (context == null) {
            context = new HashMap<>();
        }

        List<AsyncJavaProgram> programs;
        if (startExecutionId == null) {
            programs = AsyncJavaProgram.findAll(context);
            if (programs.isEmpty()) {
                getLog().info("No Java program runs in the background in this project");
                return;
            }
        } else {
            AsyncJavaProgram program = AsyncJavaProgram.find(context, startExecutionId);
            if (program == null) {
                getLog().info("No Java program of the execution '" + startExecutionId
                        + "' runs in the background in this project");
                return;
            }
            programs = Collections.singletonList(program);
        }

        MojoExecutionException failure = null;
        for (AsyncJavaProgram program : programs) {
            getLog().info("Stopping the Java program of the execution '" + program.getExecutionId() + "'");
            try {
                program.stop(context);
            } catch (MojoExecutionException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
