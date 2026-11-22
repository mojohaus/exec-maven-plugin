/*
 * Copyright 2020 Karsten Ohme.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.codehaus.mojo.exec;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Starts the test server, for the tests of the asynchronous <code>exec:java</code>.
 * <p>
 * The first argument is the port, <code>0</code> or none picks a free one. {@code main} does not return until its
 * thread is interrupted, the way a server that waits for its shutdown does, and then stops the server.
 * </p>
 *
 * @author <a href="mailto:k_o_@users.sourceforge.net ">Karsten Ohme
 * (k_o_@users.sourceforge.net )</a>
 */
public class TestServerMain {

    private static volatile HttpServer server;

    /** Counted down once the server has stopped. */
    public static volatile CountDownLatch stopped = new CountDownLatch(1);

    public static int start(int port) throws IOException {
        stopped = new CountDownLatch(1);
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        httpServer.createContext("/test", new MyHttpHandler());
        httpServer.setExecutor(Executors.newFixedThreadPool(1));
        httpServer.start();
        server = httpServer;
        System.out.println("Started test server.");
        return httpServer.getAddress().getPort();
    }

    private static class MyHttpHandler implements HttpHandler {
        public void handle(HttpExchange httpExchange) throws IOException {
            handleResponse(httpExchange);
        }

        private void handleResponse(HttpExchange httpExchange) throws IOException {
            OutputStream outputStream = httpExchange.getResponseBody();
            httpExchange.sendResponseHeaders(200, -1);
            outputStream.flush();
            outputStream.close();
        }
    }

    public static void stop() {
        HttpServer httpServer = server;
        if (httpServer != null) {
            server = null;
            httpServer.stop(0);
            Executor executor = httpServer.getExecutor();
            if (executor instanceof ExecutorService) {
                ((ExecutorService) executor).shutdown();
            }
            stopped.countDown();
        }
    }

    public static void main(String[] args) throws Exception {
        start(args.length > 0 ? Integer.parseInt(args[0]) : 0);
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            // asked to stop
        } finally {
            stop();
        }
    }
}
