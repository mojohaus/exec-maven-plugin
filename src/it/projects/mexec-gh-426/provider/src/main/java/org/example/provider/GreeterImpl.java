package org.example.provider;

import org.example.api.Greeter;

public class GreeterImpl implements Greeter {
    public String greet(String name) {
        return "Hello from ServiceLoader, " + name + "!";
    }
}
