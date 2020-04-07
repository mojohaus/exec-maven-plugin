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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Starts the test server.
 *
 * @author <a href="mailto:k_o_@users.sourceforge.net ">Karsten Ohme
 * (k_o_@users.sourceforge.net )</a>
 */
public class TestServerMain {

    public static final int SERVER_PORT = 9081;

    private static HttpServer server;

    public static void start() throws Exception {
        // simulate a delay of 5 seconds
        int i = 0;
        while (i++ < 5) {
            Thread.sleep(1000);
        }
        server = HttpServer.create(new InetSocketAddress("localhost", SERVER_PORT), 0);
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                stop();
            }
        }));
        try {
            server.createContext("/test", new MyHttpHandler());
            ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
            server.setExecutor(threadPoolExecutor);
            server.start();
            System.out.println("Started test server.");
        } catch (Exception e) {
            throw new RuntimeException("Could not start test server.", e);
        }
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
        if (server != null) {
            server.stop(0);
        }
    }

    public static void main(String[] args) throws Exception {
        start();
    }

}
