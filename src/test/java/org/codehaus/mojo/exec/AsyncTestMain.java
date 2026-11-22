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
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;

/**
 * Programs for the tests of the asynchronous <code>exec:java</code>. The first argument selects the behavior:
 * <ul>
 *   <li><code>fail</code>: throws at once.</li>
 *   <li><code>finish</code>: returns at once, leaving no thread.</li>
 *   <li><code>tcp PORT</code>: listens on the port until interrupted.</li>
 *   <li><code>late-fail PORT</code>: listens on the port and throws as soon as {@link #failNow} is released.</li>
 *   <li><code>stubborn</code>: returns, leaving a non-daemon thread that ignores interruption until
 *       {@link #release} is released.</li>
 * </ul>
 */
public class AsyncTestMain {

    public static volatile CountDownLatch failNow = new CountDownLatch(1);

    public static volatile CountDownLatch release = new CountDownLatch(1);

    /** Counted down when a thread of the program saw the interruption and left. */
    public static volatile CountDownLatch interrupted = new CountDownLatch(1);

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "fail":
                throw new IllegalStateException("boom");
            case "finish":
                return;
            case "tcp":
                try (ServerSocket socket = new ServerSocket(Integer.parseInt(args[1]))) {
                    awaitInterrupt();
                }
                return;
            case "late-fail":
                try (ServerSocket socket = new ServerSocket(Integer.parseInt(args[1]))) {
                    failNow.await();
                }
                throw new IllegalStateException("late boom");
            case "stubborn":
                Thread thread = new Thread(() -> {
                    while (true) {
                        try {
                            release.await();
                            return;
                        } catch (InterruptedException e) {
                            interrupted.countDown();
                        }
                    }
                });
                thread.setDaemon(false);
                thread.start();
                return;
            default:
                throw new IllegalArgumentException(args[0]);
        }
    }

    private static void awaitInterrupt() throws IOException {
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            interrupted.countDown();
        }
    }
}
