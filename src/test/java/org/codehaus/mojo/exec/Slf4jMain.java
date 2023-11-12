package org.codehaus.mojo.exec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Arrays.asList;

/**
 * Simple class with a main method to call from unit tests
 */
public class Slf4jMain {
    /**
     * Print on stdout the logger class (slf4j binding) and then
     * logs at info level args with Hello as suffix.
     *
     * @param args the arguments
     */
    public static void main(String... args) {
        final Logger logger = LoggerFactory.getLogger(Slf4jMain.class);
        System.out.println(logger.getClass().getName());
        logger.info("hello" + asList(args));
    }
}
