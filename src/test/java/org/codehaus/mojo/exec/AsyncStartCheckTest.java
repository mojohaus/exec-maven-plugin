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
package org.codehaus.mojo.exec;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import com.sun.net.httpserver.HttpServer;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncStartCheckTest {

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void tcpIsReadyOnlyWhileThePortIsOpen() throws Exception {
        AsyncStartCheck check;
        try (ServerSocket socket = new ServerSocket(0)) {
            check = AsyncStartCheck.create("tcp://localhost:" + socket.getLocalPort(), null);
            assertTrue(check.isReady());
        }
        assertFalse(check.isReady());
    }

    @Test
    void httpIsReadyOnlyOnA2xxStatus() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/ok", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.createContext("/missing", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.createContext("/broken", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://localhost:" + server.getAddress().getPort();
            assertTrue(AsyncStartCheck.create(base + "/ok", null).isReady());
            assertFalse(AsyncStartCheck.create(base + "/missing", null).isReady());
            assertFalse(AsyncStartCheck.create(base + "/broken", null).isReady());
        } finally {
            server.stop(0);
        }
        assertFalse(AsyncStartCheck.create("http://localhost:" + freePort() + "/", null)
                .isReady());
    }

    @Test
    void invalidAddressesFailAtOnce() {
        assertThrows(MojoExecutionException.class, () -> AsyncStartCheck.create("localhost:8080", null));
        assertThrows(MojoExecutionException.class, () -> AsyncStartCheck.create("tcp://localhost", null));
        assertThrows(MojoExecutionException.class, () -> AsyncStartCheck.create("tcp://:80", null));
        assertThrows(MojoExecutionException.class, () -> AsyncStartCheck.create("file:///tmp", null));
        assertThrows(MojoExecutionException.class, () -> AsyncStartCheck.create("not a uri", null));
    }

    @Test
    void scriptNeedsALanguageEngineAndABooleanResult() throws Exception {
        assertThrows(MojoExecutionException.class, () -> AsyncStartCheck.create("true", "no-such-language"));
        assertThrows(MojoExecutionException.class, () -> AsyncStartCheck.create(" ", "groovy"));

        assertTrue(AsyncStartCheck.create("1 + 1 == 2", "groovy").isReady());
        assertFalse(AsyncStartCheck.create("1 + 1 == 3", "groovy").isReady());
        AsyncStartCheck notBoolean = AsyncStartCheck.create("'yes'", "groovy");
        assertThrows(MojoExecutionException.class, notBoolean::isReady);
        AsyncStartCheck broken = AsyncStartCheck.create("throw new IllegalStateException('x')", "groovy");
        assertThrows(MojoExecutionException.class, broken::isReady);
    }
}
