module provider {
    requires contract;
    exports org.example.provider;
    provides org.example.api.Greeter with org.example.provider.GreeterImpl;
}
