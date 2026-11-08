package org.codehaus.mojo.exec;

/**
 * Remembers the context class loader that runs it, so a test can look at it after the mojo is done.
 */
public class LoaderProbeMain {
    static volatile ClassLoader loader;

    public static void main(String... args) {
        loader = Thread.currentThread().getContextClassLoader();
    }
}
