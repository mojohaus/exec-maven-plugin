package org.codehaus.mojo.exec;

/**
 * Testing inherited main method. Must consider superclass main methods and then pick the appropriate one.
 */
class JSR512DummySuper {
    void main(String... args) {
        StringBuilder buffer = new StringBuilder("Correct choice");

        for (String arg : args) {
            buffer.append(" ").append(arg);
        }

        System.out.println(buffer.toString());
    }
}

public class JSR512DummyMain5 extends JSR512DummySuper {
    void main() {
        System.out.println("Wrong choice");
    }
}
