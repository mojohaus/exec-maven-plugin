package org.codehaus.mojo.exec;

/**
 * Has a {@code main} method that no signature rule accepts: it takes a {@code String}, not a {@code String[]}.
 */
public class WrongSignatureMain {
    public static void main(String args) {}
}
