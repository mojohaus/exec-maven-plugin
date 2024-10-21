package org.codehaus.mojo.exec;

import org.apache.maven.toolchain.Toolchain;

public class DummyJdkToolchain implements Toolchain {

    private final String testJavaPath;

    public DummyJdkToolchain(String testJavaPath) {
        this.testJavaPath = testJavaPath;
    }

    @Override
    public String getType() {
        return "jdk";
    }

    @Override
    public String findTool(String s) {
        return "java".equals(s) ? testJavaPath : null;
    }
}
