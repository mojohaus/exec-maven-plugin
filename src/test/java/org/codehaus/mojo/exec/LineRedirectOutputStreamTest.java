package org.codehaus.mojo.exec;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.Charset;

public class LineRedirectOutputStreamTest {

    @Test
    public void givenUtf8Output_whenRedirecting_thenShouldDecodeProperly() throws IOException {
        StringBuilder sb = new StringBuilder();
        String firstLine = "Hello 😃 😄";
        String secondLine = "foo bar éà";

        try (LineRedirectOutputStream os = new LineRedirectOutputStream(sb::append)) {
            os.write(String.join("\n", firstLine, secondLine).getBytes(Charset.defaultCharset()));
        }

        Assert.assertEquals(firstLine + secondLine, sb.toString());
    }
}