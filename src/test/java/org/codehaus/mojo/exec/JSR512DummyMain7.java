package org.codehaus.mojo.exec;

/**
 * Tests a main method that's not public, static, and has parameters.
 */
public class JSR512DummyMain7 {
    static void main(String[] args) {
        StringBuilder buffer = new StringBuilder("Correct choice");

        for (String arg : args) {
            buffer.append(" ").append(arg);
        }

        System.out.println(buffer.toString());
    }
}
