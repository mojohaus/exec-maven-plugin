package org.codehaus.mojo.exec;

import javax.inject.Inject;

import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.PlexusContainer;
import org.eclipse.aether.RepositorySystem;

/**
 * Executes the supplied java class in the current VM with the enclosing project's dependencies as classpath.
 * This is the Java 9+ implementation with full JPMS support.
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
     * This Java 9+ implementation supports JPMS with full ModuleLayer and ServiceLoader support.
     * Uses Configuration.resolveAndBind() to ensure service providers are included.
     *
     * @param moduleName the module name
     * @param bootClassName the fully qualified class name
     * @throws Throwable if execution fails
     */
    @Override
    protected void doExecModulePath(final String moduleName, final String bootClassName) throws Throwable {
        // Build module path from the same elements as the classpath
        List<Path> modulePaths = new ArrayList<>();
        this.addRelevantPluginDependenciesToClasspath(modulePaths);
        this.addRelevantProjectDependenciesToClasspath(modulePaths);
        this.addAdditionalClasspathElements(modulePaths);

        getLog().debug("Module paths for JPMS execution: " + modulePaths);

        ModuleFinder finder = ModuleFinder.of(modulePaths.toArray(new Path[0]));
        ModuleLayer parent = ModuleLayer.boot();

        // Use resolveAndBind to include service providers (for ServiceLoader support)
        Configuration cf = parent.configuration().resolveAndBind(finder, ModuleFinder.of(), Set.of(moduleName));

        getLog().debug("Resolved modules: " + cf.modules());

        // Create a new module layer with a single class loader and get the controller
        ModuleLayer.Controller controller =
                ModuleLayer.defineModulesWithOneLoader(cf, List.of(parent), ClassLoader.getSystemClassLoader());
        ModuleLayer layer = controller.layer();

        // Load the main class from the module layer's class loader
        ClassLoader moduleLoader = layer.findLoader(moduleName);
        Class<?> bootClass = moduleLoader.loadClass(bootClassName);

        // Open the package containing the main class to allow reflective access
        Module bootModule = bootClass.getModule();
        String packageName = bootClass.getPackageName();
        if (bootModule != null && bootModule.isNamed()) {
            // Use the controller to add opens - this allows us to access non-exported packages
            controller.addOpens(bootModule, packageName, getClass().getModule());
            getLog().debug("Opened package " + packageName + " in module " + bootModule.getName() + " for reflection");
        }

        // Set the context class loader to the module's class loader
        Thread currentThread = Thread.currentThread();
        ClassLoader oldContextClassLoader = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(moduleLoader);
            executeMainMethodForModule(bootClass);
        } finally {
            currentThread.setContextClassLoader(oldContextClassLoader);
        }
    }

    /**
     * Execute the main method for a module-loaded class.
     * The package has already been opened by the controller in doExecModulePath.
     *
     * @param bootClass the class containing the main method
     * @throws Throwable if execution fails
     */
    private void executeMainMethodForModule(Class<?> bootClass) throws Throwable {
        try {
            java.lang.reflect.Method mainMethod = bootClass.getDeclaredMethod("main", String[].class);
            if (!Modifier.isPublic(mainMethod.getModifiers()) || !Modifier.isStatic(mainMethod.getModifiers())) {
                throw new NoSuchMethodException("main method must be public and static in " + bootClass);
            }
            mainMethod.setAccessible(true);
            mainMethod.invoke(null, (Object) arguments);
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodException("No static void main(String[] args) method found in " + bootClass);
        }
    }

    /**
     * Execute the main method with Java 9+ module opening support.
     * This overrides the base implementation to add Module.implAddOpens logic.
     *
     * @param bootClass the class containing the main method
     * @throws Throwable if execution fails
     */
    @Override
    protected void executeMainMethod(Class<?> bootClass) throws Throwable {
        // Try to find and invoke main method using reflection
        // For JPMS modules, we need to open the package to allow reflective access

        // Open the package if it's in a module
        Module bootModule = bootClass.getModule();
        if (bootModule != null && bootModule.isNamed()) {
            String packageName = bootClass.getPackageName();
            Module unnamedModule = getClass().getModule(); // This is the unnamed module
            if (!bootModule.isOpen(packageName, unnamedModule)) {
                try {
                    // Use Module.implAddOpens to open the package
                    java.lang.reflect.Method implAddOpensMethod =
                            Module.class.getDeclaredMethod("implAddOpens", String.class);
                    implAddOpensMethod.setAccessible(true);
                    implAddOpensMethod.invoke(bootModule, packageName);
                    getLog().debug("Opened package " + packageName + " in module " + bootModule.getName());
                } catch (Exception e) {
                    getLog().warn("Could not open package " + packageName + " for reflection: " + e.getMessage());
                }
            }
        }

        // Delegate to base class for the actual main method invocation
        super.executeMainMethod(bootClass);
    }
}
