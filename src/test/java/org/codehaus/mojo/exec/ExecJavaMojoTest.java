package org.codehaus.mojo.exec;

import org.codehaus.plexus.util.StringOutputStream;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepository;

import java.io.PrintStream;
import java.io.File;

/**
 * @author Jerome Lacoste <jerome@coffeebreaks.org>
 * @version $Id$
 */
public class ExecJavaMojoTest extends AbstractMojoTestCase
{
    /**
     * Check that a simple execution with no arguments and no system properties produces the expected result
     *
     * we load the config from a pom file and fill up the MavenProject property ourselves
     */
    public void testSimpleRun() throws Exception
    {
        File pom = new File( getBasedir(), "src/test/projects/project4/pom.xml" );

        ExecJavaMojo mojo;

        mojo = (ExecJavaMojo) lookupMojo( "java", pom );

        setUpProject( pom, mojo );

        MavenProject project = (MavenProject) getVariableValueFromObject( mojo, "project" );

        // why isn't this set up by the harmess based on the default-value?
        setVariableValueToObject( mojo, "killAfter", new Long(-1) );

        assertNotNull( mojo );
        assertNotNull( project );

        // trap System.out
        PrintStream out = System.out;
        StringOutputStream stringOutputStream = new StringOutputStream();
        System.setOut(new PrintStream(stringOutputStream));

        try {
            mojo.execute();
        } catch (Throwable e) {
            e.printStackTrace(System.err);
            fail(e.getMessage());
        } finally {
            System.setOut(out);
        }

        assertEquals("Hello" + System.getProperty( "line.separator" ), stringOutputStream.toString());
    }

    private void setUpProject( File pomFile, AbstractMojo mojo ) throws Exception
    {
        MavenProjectBuilder builder = (MavenProjectBuilder) lookup( MavenProjectBuilder.ROLE );

        ArtifactRepositoryLayout localRepositoryLayout =
            (ArtifactRepositoryLayout) lookup(
                ArtifactRepositoryLayout.ROLE, "default"
            );

        String path = "src/test/repository";

        ArtifactRepository localRepository =
            new DefaultArtifactRepository(
                "local", "file://" + new File( path ).getAbsolutePath(),
                localRepositoryLayout
            );

        MavenProject project = builder.buildWithDependencies( pomFile ,
                                                              localRepository, null );
        setVariableValueToObject( mojo, "project", project );
    }
}
