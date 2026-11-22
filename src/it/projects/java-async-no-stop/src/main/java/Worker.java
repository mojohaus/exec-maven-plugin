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
import java.nio.file.Files;

/** Returns from main and leaves a non-daemon thread that only ends when it is interrupted. */
public class Worker {
    public static void main(String[] args) {
        File marker = new File(args[0]);
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException e) {
                try {
                    marker.getParentFile().mkdirs();
                    Files.write(marker.toPath(), "interrupted".getBytes("UTF-8"));
                } catch (java.io.IOException io) {
                    throw new IllegalStateException(io);
                }
            }
        });
        thread.setDaemon(false);
        thread.start();
        System.out.println("Worker started");
    }
}
