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

/**
 * Will be used by {@link BlockExitTransformer} to replace {@link System#exit(int)} by this implementation.
 *
 * @author Alexander Kriegisch
 */
public final class SystemExitManager {
    private SystemExitManager() {
        // no-op
    }

    /**
     * Always throws a {@link SystemExitException} when {@link System#exit(int)} is called, instead of terminating the
     * JVM.
     * <p>
     * The exception is meant to be handled in the {@code exec:java} goal. On the one hand, this avoids that Java
     * code called in process can terminate the JVM and the whole Maven build process with it. On the other hand, the
     * exception handler can also differentiate between exit status 0 (OK) and non-0 (error) by inspecting
     * {@link SystemExitException#getExitCode()}:
     * <ul>
     *     <li>
     *         Exit status 0 (OK): Just log the fact that {@link System#exit(int)} was called.
     *     </li>
     *     <li>
     *         Exit status non-0 (error): In addition to logging, the exception is also passed on, failing the mojo
     *         execution as if the called Java code had terminated with an exception instead of trying to terminate the
     *         JVM with an error code.
     *     </li>
     * </ul>
     *
     * @param status the exit status
     */
    public static void exit(final int status) {
        throw new SystemExitException("System::exit was called with return code " + status, status);
    }
}
