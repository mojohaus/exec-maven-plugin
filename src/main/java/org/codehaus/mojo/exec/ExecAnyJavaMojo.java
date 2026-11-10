package org.codehaus.mojo.exec;

import javax.inject.Inject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.filter.DependencyFilterUtils;

/**
 * Executes the supplied java class in the current VM with parameter-supplied (artifacts) dependencies as classpath.
 *
 * @author Telmo Brugnara (tbrugz@gmail.com)
 */
@Mojo(name = "anyjava", threadSafe = true, requiresDependencyResolution = ResolutionScope.TEST, requiresProject = false)
public class ExecAnyJavaMojo extends ExecJavaMojo {

    private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile("(.+)::(.*)::(.+)");

    @Inject
    protected ExecAnyJavaMojo(org.eclipse.aether.RepositorySystem repositorySystem, PlexusContainer container) {
        super(repositorySystem, container);
    }

    /**
     * A string of the form groupId:artifactId:version[:packaging[:classifier]][,+].
     */
    @Parameter(property = "artifacts")
    private String artifacts;

    /**
     * Download transitively, retrieving the specified artifact and all of its dependencies.
     */
    @Parameter(property = "transitive", defaultValue = "true")
    private boolean transitive = true;

    /**
     * Repositories in the format id::[layout]::url or just url, separated by comma. ie.
     * central::default::https://repo.maven.apache.org/maven2,https://repo.acme.com,https://repo.acme2.com
     */
    @Parameter(property = "remoteRepositories")
    private String remoteRepositories;

    /**
     * Resolves artifact dependencies, returning Iterable&lt;Artifact&gt;
     *
     * @see <a href="https://github.com/apache/maven-dependency-plugin/blob/master/src/main/java/org/apache/maven/plugins/dependency/GetMojo.java">GetMojo</a>
     * method <code>execute</code>
     */
    protected Iterable<Artifact> resolveArtifacts() throws MojoExecutionException, MojoFailureException {
        if (artifacts == null) {
            throw new MojoFailureException("You must specify artifacts, "
                    + "e.g. -Dartifacts=org.apache.maven.plugins:maven-downloader-plugin:1.0[,<another-artifact>]");
        }

        List<org.eclipse.aether.artifact.Artifact> requestedArtifacts = new ArrayList<>();
        for (String artifact : StringUtils.split(artifacts, ",")) {
            String[] tokens = StringUtils.split(artifact, ":");
            if (tokens.length < 3 || tokens.length > 5) {
                throw new MojoFailureException("Invalid artifact, you must specify "
                        + "groupId:artifactId:version[:packaging[:classifier]] " + artifact);
            }
            String extension = tokens.length >= 4 ? tokens[3] : "jar";
            String classifier = tokens.length == 5 ? tokens[4] : "";
            String coordinates = tokens[0] + ":" + tokens[1] + ":" + extension
                    + (classifier.isEmpty() ? "" : ":" + classifier) + ":" + tokens[2];
            requestedArtifacts.add(new org.eclipse.aether.artifact.DefaultArtifact(coordinates));
        }

        try {
            RepositorySystemSession repoSession = getSession().getRepositorySession();
            List<RemoteRepository> remoteRepoList = parseRemoteRepositories(repoSession);

            List<Artifact> ret = new ArrayList<>();
            if (transitive) {
                List<Dependency> dependencies = new ArrayList<>();
                for (org.eclipse.aether.artifact.Artifact artifact : requestedArtifacts) {
                    dependencies.add(new Dependency(artifact, classpathScope));
                }

                CollectRequest collectRequest = new CollectRequest();
                collectRequest.setDependencies(dependencies);
                collectRequest.setRepositories(remoteRepoList);

                DependencyFilter classpathFilter = DependencyFilterUtils.classpathFilter(classpathScope);

                DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, classpathFilter);

                DependencyResult dependencyResult =
                        repositorySystem.resolveDependencies(repoSession, dependencyRequest);

                for (ArtifactResult artifactResult : dependencyResult.getArtifactResults()) {
                    ret.add(RepositoryUtils.toArtifact(artifactResult.getArtifact()));
                }
            } else {
                for (org.eclipse.aether.artifact.Artifact artifact : requestedArtifacts) {
                    ArtifactRequest artifactRequest = new ArtifactRequest(artifact, remoteRepoList, null);
                    ArtifactResult artifactResult = repositorySystem.resolveArtifact(repoSession, artifactRequest);
                    ret.add(RepositoryUtils.toArtifact(artifactResult.getArtifact()));
                }
            }
            return ret;
        } catch (ArtifactResolutionException | DependencyResolutionException e) {
            throw new MojoExecutionException("Couldn't download artifact: " + e.getMessage(), e);
        }
    }

    /*
     * @see <a href="https://github.com/apache/maven-dependency-plugin/blob/master/src/main/java/org/apache/maven/plugins/dependency/GetMojo.java">GetMojo</a>
     * method <code>parseRepository</code>
     */
    protected List<RemoteRepository> parseRemoteRepositories(RepositorySystemSession repoSession)
            throws MojoFailureException {
        List<RemoteRepository> remoteRepoList = new ArrayList<>();

        if (remoteRepositories != null) {
            // Use the same format as in the deploy plugin id::layout::url
            String[] repos = StringUtils.split(remoteRepositories, ",");
            for (String repo : repos) {
                remoteRepoList.add(parseRepository(repo));
            }
        }

        if (remoteRepoList.isEmpty()) {
            // fallback to central so that the resolver always has a connector to use
            remoteRepoList.add(
                    new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2").build());
        }

        return remoteRepoList;
    }

    /*
     * @see <a href="https://github.com/apache/maven-dependency-plugin/blob/master/src/main/java/org/apache/maven/plugins/dependency/GetMojo.java">GetMojo</a>
     * method <code>parseRepository</code>
     */
    protected RemoteRepository parseRepository(String repo) throws MojoFailureException {
        // if it's a simple url
        String id = "temp";
        String layout = "default";
        String url = repo;

        // if it's an extended repo URL of the form id::layout::url
        if (repo.contains("::")) {
            Matcher matcher = ALT_REPO_SYNTAX_PATTERN.matcher(repo);
            if (!matcher.matches()) {
                throw new MojoFailureException(
                        repo,
                        "Invalid syntax for repository: " + repo,
                        "Invalid syntax for repository. Use \"id::layout::url\" or \"URL\".");
            }

            id = matcher.group(1).trim();
            if (!StringUtils.isEmpty(matcher.group(2))) {
                layout = matcher.group(2).trim();
            }
            url = matcher.group(3).trim();
        }
        return new RemoteRepository.Builder(id, layout, url).build();
    }

    @Override
    protected java.net.URLClassLoader getClassLoader() throws MojoExecutionException {
        List<Path> classpathURLs = new ArrayList<>();
        this.addArtifactParametersToClasspath(classpathURLs);
        this.addRelevantPluginDependenciesToClasspath(classpathURLs);
        this.addRelevantProjectDependenciesToClasspath(classpathURLs);
        this.addAdditionalClasspathElements(classpathURLs);

        try {
            return URLClassLoaderBuilder.builder()
                    .setLogger(getLog())
                    .setPaths(classpathURLs)
                    .setExclusions(classpathFilenameExclusions)
                    .setForcedJvmPackages(forcedJvmPackages)
                    .setExcludedJvmPackages(excludedJvmPackages)
                    .withTransformers(blockSystemExit)
                    .build();
        } catch (NullPointerException | IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    /**
     * Add any artifact parameter to the classpath.
     *
     * @param path classpath of {@link java.net.URL} objects
     * @throws MojoExecutionException if a problem happens
     */
    protected void addArtifactParametersToClasspath(List<Path> path) throws MojoExecutionException {
        try {
            for (Artifact classPathElement : this.resolveArtifacts()) {
                getLog().debug("Adding parameter artifact: " + classPathElement.getArtifactId() + " to classpath");
                path.add(classPathElement.getFile().toPath());
            }
        } catch (MojoFailureException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }
}
