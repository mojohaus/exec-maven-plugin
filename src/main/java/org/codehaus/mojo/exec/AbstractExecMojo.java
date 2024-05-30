package org.codehaus.mojo.exec;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.filter.DependencyFilterUtils;

/**
 * This class is used for unifying functionality between the 2 mojo exec plugins ('java' and 'exec'). It handles parsing
 * the arguments and adding source/test folders.
 *
 * @author Philippe Jacot (PJA)
 * @author Jerome Lacoste
 */
public abstract class AbstractExecMojo extends AbstractMojo {

    @Component
    protected RepositorySystem repositorySystem;

    /**
     * The enclosing project.
     */
    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    /**
     * The current build session instance. This is used for toolchain manager API calls.
     */
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Parameter(readonly = true, defaultValue = "${plugin.artifacts}")
    private List<Artifact> pluginDependencies;

    /**
     * If provided the ExecutableDependency identifies which of the plugin dependencies contains the executable class.
     * This will have the effect of only including plugin dependencies required by the identified ExecutableDependency.
     *
     * <p>
     * If includeProjectDependencies is set to <code>true</code>, all of the project dependencies will be included on
     * the executable's classpath. Whether a particular project dependency is a dependency of the identified
     * ExecutableDependency will be irrelevant to its inclusion in the classpath.
     * </p>
     *
     * @since 1.1-beta-1
     */
    @Parameter
    protected ExecutableDependency executableDependency;

    /**
     * This folder is added to the list of those folders containing source to be compiled. Use this if your plugin
     * generates source code.
     */
    @Parameter(property = "sourceRoot")
    private File sourceRoot;

    /**
     * This folder is added to the list of those folders containing source to be compiled for testing. Use this if your
     * plugin generates test source code.
     */
    @Parameter(property = "testSourceRoot")
    private File testSourceRoot;

    /**
     * Arguments separated by space for the executed program. For example: "-j 20"
     */
    @Parameter(property = "exec.args")
    private String commandlineArgs;

    /**
     * Defines the scope of the classpath passed to the plugin.
     *
     * <ul>
     *     <li><code>runtime</code> (default): Include "compile" and "runtime" scopes</li>
     *     <li><code>compile</code>: Include "compile", "provided", and "system" scopes</li>
     *     <li><code>test</code>: Include all scopes</li>
     *     <li><code>provided</code>: Include "compile", "runtime", "provided", and "system" scopes</li>
     *     <li><code>system</code>: Include "system" scope</li>
     * </ul>
     *
     * Since 1.1.2, the default value is 'runtime' instead of 'compile'.
     */
    @Parameter(property = "exec.classpathScope", defaultValue = "runtime")
    protected String classpathScope;

    /**
     * Skip the execution. Starting with version 1.4.0 the former name <code>skip</code> has been changed into
     * <code>exec.skip</code>.
     *
     * @since 1.0.1
     */
    // TODO: Remove the alias in version 1.5.0 of the plugin.
    @Parameter(property = "exec.skip", defaultValue = "false", alias = "skip")
    private boolean skip;

    /**
     * Add project resource directories to classpath. This is especially useful if the exec plugin is used for a code
     * generator that reads its settings from the classpath.
     *
     * @since 1.5.1
     */
    @Parameter(property = "addResourcesToClasspath", defaultValue = "false")
    private boolean addResourcesToClasspath;

    /**
     * Add project output directory to classpath. This might be undesirable when the exec plugin is run before the
     * compile step. Default is <code>true</code>.
     *
     * @since 1.5.1
     */
    @Parameter(property = "addOutputToClasspath", defaultValue = "true")
    private boolean addOutputToClasspath;

    /**
     * Indicates if this plugin's dependencies should be used when executing the main class.
     * <p>
     * This is useful when project dependencies are not appropriate. Using only the plugin dependencies can be
     * particularly useful when the project is not a java project. For example a mvn project using the csharp plugins
     * only expects to see dotnet libraries as dependencies.
     * </p>
     *
     * @since 3.4.0
     */
    @Parameter(property = "exec.includePluginsDependencies", defaultValue = "false")
    protected boolean includePluginDependencies;

    /**
     * Collects the project artifacts in the specified List and the project specific classpath (build output and build
     * test output) Files in the specified List, depending on the plugin classpathScope value.
     *
     * @param artifacts the list where to collect the scope specific artifacts
     * @param theClasspathFiles the list where to collect the scope specific output directories
     */
    protected void collectProjectArtifactsAndClasspath(List<Artifact> artifacts, List<Path> theClasspathFiles) {
        if (addResourcesToClasspath) {
            for (Resource r : project.getBuild().getResources()) {
                theClasspathFiles.add(Paths.get(r.getDirectory()));
            }
        }

        if ("compile".equals(classpathScope)) {
            artifacts.addAll(project.getCompileArtifacts());
            if (addOutputToClasspath) {
                theClasspathFiles.add(Paths.get(project.getBuild().getOutputDirectory()));
            }
        } else if ("test".equals(classpathScope)) {
            artifacts.addAll(project.getTestArtifacts());
            if (addOutputToClasspath) {
                theClasspathFiles.add(Paths.get(project.getBuild().getTestOutputDirectory()));
                theClasspathFiles.add(Paths.get(project.getBuild().getOutputDirectory()));
            }
        } else if ("runtime".equals(classpathScope)) {
            artifacts.addAll(project.getRuntimeArtifacts());
            if (addOutputToClasspath) {
                theClasspathFiles.add(Paths.get(project.getBuild().getOutputDirectory()));
            }
        } else if ("provided".equals(classpathScope)) {
            // "compile" gives compile, provided, and system scopes
            // "runtime" gives compile and runtime scopes
            Set<Artifact> artifactSet = new HashSet<>(project.getCompileArtifacts());
            artifactSet.addAll(project.getRuntimeArtifacts());
            artifacts.addAll(artifactSet);
            if (addOutputToClasspath) {
                theClasspathFiles.add(Paths.get(project.getBuild().getOutputDirectory()));
            }
        } else if ("system".equals(classpathScope)) {
            artifacts.addAll(project.getSystemArtifacts());
        } else {
            throw new IllegalStateException("Invalid classpath scope: " + classpathScope);
        }

        getLog().debug("Collected project artifacts " + artifacts);
        getLog().debug("Collected project classpath " + theClasspathFiles);
    }

    /**
     * Parses the argument string given by the user. Strings are recognized as everything between STRING_WRAPPER.
     * PARAMETER_DELIMITER is ignored inside a string. STRING_WRAPPER and PARAMETER_DELIMITER can be escaped using
     * ESCAPE_CHAR.
     *
     * @return Array of String representing the arguments
     * @throws MojoExecutionException for wrong formatted arguments
     */
    protected String[] parseCommandlineArgs() throws MojoExecutionException {
        if (commandlineArgs == null) {
            return null;
        } else {
            try {
                return CommandLineUtils.translateCommandline(commandlineArgs);
            } catch (Exception e) {
                throw new MojoExecutionException(e.getMessage());
            }
        }
    }

    /**
     * @return true of the mojo has command line arguments
     */
    protected boolean hasCommandlineArgs() {
        return (commandlineArgs != null);
    }

    /**
     * Register compile and compile tests source roots if necessary
     */
    protected void registerSourceRoots() {
        if (sourceRoot != null) {
            getLog().info("Registering compile source root " + sourceRoot);
            project.addCompileSourceRoot(sourceRoot.toString());
        }

        if (testSourceRoot != null) {
            getLog().info("Registering compile test source root " + testSourceRoot);
            project.addTestCompileSourceRoot(testSourceRoot.toString());
        }
    }

    /**
     * Check if the execution should be skipped
     *
     * @return true to skip
     */
    protected boolean isSkip() {
        return skip;
    }

    protected final MavenSession getSession() {
        return session;
    }

    protected final List<Artifact> getPluginDependencies() {
        return pluginDependencies;
    }

    /**
     * Examine the plugin dependencies to find the executable artifact.
     *
     * @return an artifact which refers to the actual executable tool (not a POM)
     * @throws MojoExecutionException if no executable artifact was found
     */
    protected Artifact findExecutableArtifact() throws MojoExecutionException {
        // ILimitedArtifactIdentifier execToolAssembly = this.getExecutableToolAssembly();

        Artifact executableTool = null;
        for (Artifact pluginDep : this.pluginDependencies) {
            if (this.executableDependency.matches(pluginDep)) {
                executableTool = pluginDep;
                break;
            }
        }

        if (executableTool == null) {
            throw new MojoExecutionException("No dependency of the plugin matches the specified executableDependency."
                    + "  Specified executableToolAssembly is: " + executableDependency.toString());
        }

        return executableTool;
    }

    /**
     * Determine all plugin dependencies relevant to the executable. Takes includePlugins, and the executableDependency
     * into consideration.
     *
     * @return a set of Artifact objects. (Empty set is returned if there are no relevant plugin dependencies.)
     * @throws MojoExecutionException if a problem happens resolving the plufin dependencies
     */
    protected Set<Artifact> determineRelevantPluginDependencies() throws MojoExecutionException {
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
}
