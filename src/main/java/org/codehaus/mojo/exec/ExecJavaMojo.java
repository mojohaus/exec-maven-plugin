package org.codehaus.mojo.exec;

import javax.inject.Inject;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.PlexusContainer;
import org.eclipse.aether.RepositorySystem;

/**
 * Executes the supplied java class in the current VM with the enclosing project's dependencies as classpath.
 * This is the Java 8 base implementation.
 *
 * @author Kaare Nilsen (kaare.nilsen@gmail.com), David Smiley (dsmiley@mitre.org)
 * @since 1.0
 */
@Mojo(name = "java", threadSafe = true, requiresDependencyResolution = ResolutionScope.TEST)
public class ExecJavaMojo extends AbstractExecJavaBase {

    @Inject
    protected ExecJavaMojo(RepositorySystem repositorySystem, PlexusContainer container) {
        super(repositorySystem, container);
    }

    /**
     * Execute using module path (Java 9+ JPMS).
     * This base implementation (Java 8) does not support JPMS and will throw an error.
     * The Java 9+ version overrides this method with full JPMS support.
     *
     * @param moduleName the module name
     * @param bootClassName the fully qualified class name
     * @throws Throwable if execution fails
     */
    @Override
    protected void doExecModulePath(final String moduleName, final String bootClassName) throws Throwable {
        throw new UnsupportedOperationException(
                "Module path execution (moduleName/className syntax) requires Java 9 or later. "
                        + "Current Java version: " + System.getProperty("java.version") + ". "
                        + "Either upgrade to Java 9+ or use the classpath-only syntax (just the className without module prefix).");
    }
}
