package org.codehaus.mojo.exec;

import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class HelloRunnable implements Runnable {
    private final Function<String, String> versionResolver;
    private final Properties properties;
    private final BiConsumer<String, String> updater;

    public HelloRunnable(
            final Function<String, String> highestVersionResolver,
            final Properties systemProperties,
            final BiConsumer<String, String> systemPropertiesUpdater) {
        this.versionResolver = highestVersionResolver;
        this.properties = systemProperties;
        this.updater = systemPropertiesUpdater;
    }

    public void run() {
        final String v = properties.getProperty("test.version");
        updater.accept("hello.runnable.output", v + ": " + (versionResolver != null));
    }
}
