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

import java.io.File;
import java.net.ServerSocket;
import java.nio.file.Files;

/** Listens on a port until it is interrupted, like a server that waits for its shutdown. */
public class Server {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(args[0]);
        File marker = new File(args[1]);
        try (ServerSocket socket = new ServerSocket(port)) {
            System.out.println("Server listening on " + port);
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            marker.getParentFile().mkdirs();
            Files.write(marker.toPath(), "stopped".getBytes("UTF-8"));
        }
    }
}
