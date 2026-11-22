package org.codehaus.mojo.exec;

/*
 * Copyright MojoHaus and Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.maven.plugin.MojoExecutionException;

/**
 * Decides whether a program that {@code exec:java} started in the background is ready to be used.
 * <p>
 * The check is polled until it is true. Without a script language it is an address: {@code tcp://host:port} is
 * ready once a connection to the port opens, and an {@code http://} or {@code https://} URL is ready once a GET
 * request answers with a 2xx status. With a script language it is a script that must evaluate to a
 * {@link Boolean}.
 *
 * @since 3.8.0
 */
abstract class AsyncStartCheck {

    private static final int CONNECT_TIMEOUT_MILLIS = 1000;

    private static final int READ_TIMEOUT_MILLIS = 2000;

    /**
     * @return <code>true</code> when the program is ready, <code>false</code> when it is not ready yet
     * @throws MojoExecutionException if the check itself is broken, so that waiting longer cannot help
     */
    abstract boolean isReady() throws MojoExecutionException;

    /**
     * Creates the check, failing early on a configuration that can never work.
     *
     * @param check the address or the script
     * @param scriptLanguage the JSR-223 language name of the script, <code>null</code> for an address
     * @return the check
     * @throws MojoExecutionException if the configuration is invalid
     */
    static AsyncStartCheck create(String check, String scriptLanguage) throws MojoExecutionException {
        if (scriptLanguage != null && !scriptLanguage.trim().isEmpty()) {
            return script(check, scriptLanguage.trim());
        }
        return address(check);
    }

    private static AsyncStartCheck script(String script, String language) throws MojoExecutionException {
        if (script == null || script.trim().isEmpty()) {
            throw new MojoExecutionException(
                    "asyncStartCheckScriptLanguage is set to " + language + " but asyncStartCheck holds no script");
        }
        final ScriptEngine engine = new ScriptEngineManager().getEngineByName(language);
        if (engine == null) {
            throw new MojoExecutionException("Couldn't find an engine for the script language " + language
                    + ". Add a JSR-223 implementation for it as a dependency of the plugin.");
        }
        return new AsyncStartCheck() {
            @Override
            boolean isReady() throws MojoExecutionException {
                final Object result;
                try {
                    result = engine.eval(script);
                } catch (ScriptException e) {
                    throw new MojoExecutionException("Couldn't evaluate the asyncStartCheck script.", e);
                }
                if (!(result instanceof Boolean)) {
                    throw new MojoExecutionException(
                            "The asyncStartCheck script must evaluate to a boolean, but it returned " + result);
                }
                return (Boolean) result;
            }
        };
    }

    private static AsyncStartCheck address(String check) throws MojoExecutionException {
        final URI uri;
        try {
            uri = new URI(check.trim());
        } catch (URISyntaxException e) {
            throw new MojoExecutionException("The asyncStartCheck '" + check + "' is not a valid address", e);
        }
        final String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        switch (scheme) {
            case "tcp":
                if (uri.getHost() == null || uri.getPort() < 0) {
                    throw new MojoExecutionException(
                            "The asyncStartCheck '" + check + "' must have the form tcp://host:port");
                }
                final InetSocketAddress socketAddress = new InetSocketAddress(uri.getHost(), uri.getPort());
                return new AsyncStartCheck() {
                    @Override
                    boolean isReady() {
                        try (Socket socket = new Socket()) {
                            socket.connect(socketAddress, CONNECT_TIMEOUT_MILLIS);
                            return true;
                        } catch (IOException e) {
                            return false;
                        }
                    }
                };
            case "http":
            case "https":
                if (uri.getHost() == null) {
                    throw new MojoExecutionException("The asyncStartCheck '" + check + "' has no host");
                }
                final URL url;
                try {
                    url = uri.toURL();
                } catch (java.net.MalformedURLException e) {
                    throw new MojoExecutionException("The asyncStartCheck '" + check + "' is not a valid URL", e);
                }
                return new AsyncStartCheck() {
                    @Override
                    boolean isReady() {
                        HttpURLConnection connection = null;
                        try {
                            connection = (HttpURLConnection) url.openConnection();
                            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
                            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
                            int status = connection.getResponseCode();
                            return status >= 200 && status < 300;
                        } catch (IOException e) {
                            return false;
                        } finally {
                            if (connection != null) {
                                connection.disconnect();
                            }
                        }
                    }
                };
            default:
                throw new MojoExecutionException("The asyncStartCheck '" + check
                        + "' must start with tcp://, http:// or https://, or asyncStartCheckScriptLanguage must"
                        + " name the language of a script");
        }
    }
}
