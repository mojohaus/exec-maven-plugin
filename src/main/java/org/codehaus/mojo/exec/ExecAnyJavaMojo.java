package org.codehaus.mojo.exec;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.settings.Settings;
import org.apache.maven.shared.artifact.filter.resolve.AbstractFilter;
import org.apache.maven.shared.artifact.filter.resolve.Node;
import org.apache.maven.shared.artifact.filter.resolve.TransformableFilter;
import org.apache.maven.shared.transfer.artifact.ArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.DefaultArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResult;
import org.apache.maven.shared.transfer.dependencies.DefaultDependableCoordinate;
import org.apache.maven.shared.transfer.dependencies.DependableCoordinate;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolverException;
import org.codehaus.plexus.util.StringUtils;

/**
 * Executes the supplied java class in the current VM with parameter-supplied (artifacts) dependencies as classpath.
 */
@Mojo( name = "anyjava", threadSafe = true, requiresDependencyResolution = ResolutionScope.TEST, requiresProject = false )
public class ExecAnyJavaMojo
    extends ExecJavaMojo
{

    private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile( "(.+)::(.*)::(.+)" );

    @Component( role = ArtifactRepositoryLayout.class )
    private Map<String, ArtifactRepositoryLayout> repositoryLayouts;

    @Component
    private RepositorySystem repositorySystem;

    @Component
    private ArtifactHandlerManager artifactHandlerManager;

    private final List<DefaultDependableCoordinate> coordinates = new ArrayList<>();

    /**
     * A string of the form groupId:artifactId:version[:packaging[:classifier]][,+].
     */
    @Parameter( property = "artifacts" )
    private String artifacts;

    /**
     * Download transitively, retrieving the specified artifact and all of its dependencies.
     */
    @Parameter( property = "transitive", defaultValue = "true" )
    private boolean transitive = true;

    /**
     * Repositories in the format id::[layout]::url or just url, separated by comma. ie.
     * central::default::https://repo.maven.apache.org/maven2,myrepo::::https://repo.acme.com,https://repo.acme2.com
     */
    @Parameter( property = "remoteRepositories" )
    private String remoteRepositories;

    @Parameter( defaultValue = "${project.remoteArtifactRepositories}", readonly = true, required = true )
    private List<ArtifactRepository> pomRemoteRepositories;

    /**
     * Resolves artifacts, as provided by the "artifacts" parameter, optionally with transitive dependencies
     *
     * @return an Iterable of Artifact objects.
     * @throws MojoExecutionException if a problem happens
     * @throws MojoFailureException if a problem happens
     * @see <a href="https://github.com/apache/maven-dependency-plugin/blob/master/src/main/java/org/apache/maven/plugins/dependency/GetMojo.java">
     *       org.apache.maven.plugins.dependency.GetMojo#execute()</a>
     */
    protected Iterable<Artifact> resolveArtifacts()
        throws MojoExecutionException, MojoFailureException
    {
        if ( coordinates.size() == 0 && artifacts == null )
        {
            throw new MojoFailureException( "You must specify artifacts, "
                + "e.g. -Dartifacts=org.apache.maven.plugins:maven-downloader-plugin:1.0[,<another-artifact>]" );
        }
        if ( artifacts != null )
        {
            String[] artifactsArr = StringUtils.split( artifacts, "," );
            for ( String artifact : artifactsArr ) {
                DefaultDependableCoordinate coordinate = new DefaultDependableCoordinate();
                String[] tokens = StringUtils.split( artifact, ":" );
                if ( tokens.length < 3 || tokens.length > 5 )
                {
                    throw new MojoFailureException( "Invalid artifact, you must specify "
                        + "groupId:artifactId:version[:packaging[:classifier]] " + artifact );
                }
                coordinate.setGroupId( tokens[0] );
                coordinate.setArtifactId( tokens[1] );
                coordinate.setVersion( tokens[2] );
                if ( tokens.length >= 4 )
                {
                    coordinate.setType( tokens[3] );
                }
                if ( tokens.length == 5 )
                {
                    coordinate.setClassifier( tokens[4] );
                }
                coordinates.add(coordinate);
            }
        }

        ArtifactRepositoryPolicy always =
            new ArtifactRepositoryPolicy( true, ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS,
                                          ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN );

        List<ArtifactRepository> repoList = new ArrayList<>();

        if ( pomRemoteRepositories != null )
        {
            repoList.addAll( pomRemoteRepositories );
        }

        if ( remoteRepositories != null )
        {
            // Use the same format as in the deploy plugin id::layout::url
            String[] repos = StringUtils.split( remoteRepositories, "," );
            for ( String repo : repos )
            {
                repoList.add( parseRepository( repo, always ) );
            }
        }

        try
        {
            ProjectBuildingRequest buildingRequest =
                new DefaultProjectBuildingRequest( getSession().getProjectBuildingRequest() );

            Settings settings = getSession().getSettings();
            repositorySystem.injectMirror( repoList, settings.getMirrors() );
            repositorySystem.injectProxy( repoList, settings.getProxies() );
            repositorySystem.injectAuthentication( repoList, settings.getServers() );

            buildingRequest.setRemoteRepositories( repoList );
            TransformableFilter nopFilter = new NopTransformableFilter();

            List<Artifact> ret = new ArrayList<>();
            if ( transitive )
            {
                for ( DefaultDependableCoordinate coordinate : coordinates ) {
                    getLog().info( "Resolving " + coordinate + " with transitive dependencies" );
                    for ( ArtifactResult ar: getDependencyResolver().resolveDependencies( buildingRequest,
                                                                                          coordinate, nopFilter ) ) {
                        ret.add(ar.getArtifact());
                    }
                }
            }
            else
            {
                for ( DefaultDependableCoordinate coordinate : coordinates ) {
                    getLog().info( "Resolving " + coordinate );
                    ret.add( getArtifactResolver().resolveArtifact( buildingRequest, toArtifactCoordinate( coordinate ) )
                                                  .getArtifact() );
                }
            }
            return ret;
        }
        catch ( ArtifactResolverException | DependencyResolverException e )
        {
            throw new MojoExecutionException( "Couldn't download artifact: " + e.getMessage(), e );
        }
    }

    @Override
    protected URLClassLoader getClassLoader()
        throws MojoExecutionException
    {
        List<Path> classpathURLs = new ArrayList<>();
        this.addArtifactParametersToClasspath( classpathURLs );
        this.addRelevantPluginDependenciesToClasspath( classpathURLs );
        this.addRelevantProjectDependenciesToClasspath( classpathURLs );
        this.addAdditionalClasspathElements( classpathURLs );

        try
        {
            return URLClassLoaderBuilder.builder()
                    .setLogger( getLog() )
                    .setPaths( classpathURLs )
                    .setExclusions( getClasspathFilenameExclusions() )
                    .build();
        }
        catch ( NullPointerException | IOException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }

    }

    /**
     * Add any artifact parameter to the classpath.
     *
     * @param path classpath of {@link java.net.URL} objects
     * @throws MojoExecutionException if a problem happens
     */
    protected void addArtifactParametersToClasspath( List<Path> path )
        throws MojoExecutionException
    {
        try {
            for ( Artifact classPathElement : this.resolveArtifacts() )
            {
                getLog().debug( "Adding parameter artifact: " + classPathElement.getArtifactId()
                    + " to classpath" );
                path.add( classPathElement.getFile().toPath() );
            }
        }
        catch ( MojoFailureException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }
    }

    /*
     * @see <a href="https://github.com/apache/maven-dependency-plugin/blob/master/src/main/java/org/apache/maven/plugins/dependency/GetMojo.java">
     *       org.apache.maven.plugins.dependency.GetMojo#parseRepository(String, ArtifactRepositoryPolicy)</a>
     */
    protected ArtifactRepository parseRepository( String repo, ArtifactRepositoryPolicy policy )
        throws MojoFailureException
    {
        // if it's a simple url
        String id = "temp";
        ArtifactRepositoryLayout layout = getLayout( "default" );
        String url = repo;

        // if it's an extended repo URL of the form id::layout::url
        if ( repo.contains( "::" ) )
        {
            Matcher matcher = ALT_REPO_SYNTAX_PATTERN.matcher( repo );
            if ( !matcher.matches() )
            {
                throw new MojoFailureException( repo, "Invalid syntax for repository: " + repo,
                                                "Invalid syntax for repository. Use \"id::layout::url\" or \"URL\"." );
            }

            id = matcher.group( 1 ).trim();
            if ( !StringUtils.isEmpty( matcher.group( 2 ) ) )
            {
                layout = getLayout( matcher.group( 2 ).trim() );
            }
            url = matcher.group( 3 ).trim();
        }
        return new MavenArtifactRepository( id, url, layout, policy, policy );
    }

    /*
     * @see <a href="https://github.com/apache/maven-dependency-plugin/blob/master/src/main/java/org/apache/maven/plugins/dependency/GetMojo.java">
     *       org.apache.maven.plugins.dependency.GetMojo#getLayout(String)</a>
     */
    private ArtifactRepositoryLayout getLayout( String id )
        throws MojoFailureException
    {
        ArtifactRepositoryLayout layout = repositoryLayouts.get( id );

        if ( layout == null )
        {
            throw new MojoFailureException( id, "Invalid repository layout", "Invalid repository layout: " + id );
        }

        return layout;
    }

    /*
     * @see <a href="https://github.com/apache/maven-dependency-plugin/blob/master/src/main/java/org/apache/maven/plugins/dependency/GetMojo.java">
     *       org.apache.maven.plugins.dependency.GetMojo#toArtifactCoordinate(DependableCoordinate)</a>
     */
    private ArtifactCoordinate toArtifactCoordinate( DependableCoordinate dependableCoordinate )
    {
        ArtifactHandler artifactHandler = artifactHandlerManager.getArtifactHandler( dependableCoordinate.getType() );
        DefaultArtifactCoordinate artifactCoordinate = new DefaultArtifactCoordinate();
        artifactCoordinate.setGroupId( dependableCoordinate.getGroupId() );
        artifactCoordinate.setArtifactId( dependableCoordinate.getArtifactId() );
        artifactCoordinate.setVersion( dependableCoordinate.getVersion() );
        artifactCoordinate.setClassifier( dependableCoordinate.getClassifier() );
        artifactCoordinate.setExtension( artifactHandler.getExtension() );
        return artifactCoordinate;
    }

    private static class NopTransformableFilter extends AbstractFilter
    {
        @Override
        public boolean accept( Node node, List<Node> parents )
        {
            return true;
        }
    }

}
