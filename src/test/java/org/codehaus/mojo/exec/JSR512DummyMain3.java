package org.codehaus.mojo.exec;

/**
 * Tests invocation of static main method without args.
 */
public class JSR512DummyMain3 {
    int main(String... args) {
        StringBuilder buffer = new StringBuilder("Wrong choice");

        for (String arg : args) {
            buffer.append(System.getProperty("line.separator")).append(arg);
        }

        System.out.println(buffer.toString());
        return buffer.length();
    }

    static void main() {
        System.out.println("Correct choice");
    }
}
