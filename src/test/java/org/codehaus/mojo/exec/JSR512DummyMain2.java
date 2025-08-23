package org.codehaus.mojo.exec;

/**
 * Tests that the main method with String[] parameter is preferred over that with no parameters.
 */
public class JSR512DummyMain2 {
    void main(String... args) {
        StringBuilder buffer = new StringBuilder("Correct choice");

        for (String arg : args) {
            buffer.append(" ").append(arg);
        }

        System.out.println(buffer.toString());
    }

    void main() {
        System.out.println("Wrong choice");
    }
}
