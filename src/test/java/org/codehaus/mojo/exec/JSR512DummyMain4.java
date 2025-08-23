package org.codehaus.mojo.exec;

/**
 * Testing failure with an instance main and private no-arg constructor.
 */
public class JSR512DummyMain4 {
    private JSR512DummyMain4() {}

    void main() {
        System.out.println("Wrong choice");
    }
}
