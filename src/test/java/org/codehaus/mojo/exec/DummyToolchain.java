package org.codehaus.mojo.exec;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.maven.toolchain.Toolchain;

public class DummyToolchain implements Toolchain {

    private final String testJavaPath;

    public DummyToolchain(String testJavaPath) {
        this.testJavaPath = testJavaPath;
    }

    @Override
    public String getType() {
        throw new NotImplementedException("testToolchain");
    }

    @Override
    public String findTool(String s) {
        return "java".equals(s) ? testJavaPath : null;
    }
}
