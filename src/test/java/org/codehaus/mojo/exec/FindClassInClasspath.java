package org.codehaus.mojo.exec;

/**
 * @author <a href="mailto:jerome@coffeebreaks.org">Jerome Lacoste</a>
 */
public class FindClassInClasspath {
    public static final String FOUND_ALL = "OK";

    /**
     * @param args the names of classes to search in the classpath prints 'OK' if all classes found
     **/
    public static void main(String... args) {
        for (String arg : args) {
            if (!isClassInClasspath(arg)) {
                System.out.println("ERROR: class + " + arg + " not found in classpath");
                System.exit(1);
            }
        }
        System.out.println(FOUND_ALL);
    }

    private static boolean isClassInClasspath(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
            return false;
        }
    }
}
