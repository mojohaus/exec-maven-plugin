package org.codehaus.mojo.exec;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class LineRedirectOutputStreamTest {

    @Test
    public void givenExtendedUnicodeCharacterOutput_whenRedirectingWithUtf8Charset_thenShouldDecodeProperly()
            throws IOException {
        internalTestForCharset(StandardCharsets.UTF_8);
    }

    @Test
    public void givenExtendedUnicodeCharacterOutput_whenRedirectingWithIso8859Charset_thenShouldDecodeProperly()
            throws IOException {
        internalTestForCharset(StandardCharsets.ISO_8859_1);
    }

    @Test
    public void givenExtendedUnicodeCharacterOutput_whenRedirectingWithCp1252_thenShouldDecodeProperly()
            throws IOException {
        Assume.assumeTrue(
                "The JVM does not contain the cp-1252 charset",
                Charset.availableCharsets().containsKey("windows-1252"));
        internalTestForCharset(Charset.forName("windows-1252"));
    }

    @Test
    public void givenExtendedUnicodeCharacterOutput_whenRedirectingWithDefaultCharset_thenShouldDecodeProperly()
            throws IOException {
        internalTestForCharset(Charset.defaultCharset());
    }

    @Test
    public void givenExtendedUnicodeCharacterOutput_whenRedirectingWithCharsetUnspecified_thenShouldDecodeProperly()
            throws IOException {
        internalTestForCharset(sb -> new LineRedirectOutputStream(sb::append), Charset.defaultCharset());
    }

    @Test(expected = NullPointerException.class)
    public void givenNullCharset_whenInstantiating_thenShouldThrow() {
        new LineRedirectOutputStream(new StringBuilder()::append, null);
    }

    @Test(expected = NullPointerException.class)
    public void givenNullStringConsumer_whenInstantiating_thenShouldThrow() {
        new LineRedirectOutputStream(null, Charset.defaultCharset());
    }

    private void internalTestForCharset(Charset charset) throws IOException {
        internalTestForCharset(sb -> new LineRedirectOutputStream(sb::append, charset), charset);
    }

    private void internalTestForCharset(
            Function<StringBuilder, LineRedirectOutputStream> lineRedirectOutputStream, Charset charset)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        String firstLine = "Hello, 你好, नमस्ते, مرحبا, γεια σας, שלום, こんにちは, 안녕하세요!";
        String secondLine = "🌍 Welcome to the world! 🌟✨🎉🔥";
        String expectedString = firstLine + secondLine;

        try (LineRedirectOutputStream os = lineRedirectOutputStream.apply(sb)) {
            os.write(String.join("\n", firstLine, secondLine).getBytes(charset));
        }

        // The String to bytes to String is required here because StringCoding uses the Charset.defaultCharset()
        // internally so it would make the test fail when testing for different charsets.
        Assert.assertEquals(new String(expectedString.getBytes(charset), charset), sb.toString());
    }
}
