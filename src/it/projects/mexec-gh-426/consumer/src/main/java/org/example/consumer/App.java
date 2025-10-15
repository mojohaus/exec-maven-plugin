package org.example.consumer;

import java.util.ServiceLoader;
import org.example.api.Greeter;

public class App {
    public static void main(String[] args) {
        ServiceLoader<Greeter> loader = ServiceLoader.load(Greeter.class);

        Greeter greeter = loader.findFirst()
            .orElseThrow(() -> new RuntimeException("No Greeter service provider found"));

        String message = greeter.greet("World");
        System.out.println(message);
    }
}
