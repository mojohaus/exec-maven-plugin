/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.codehaus.mojo.exec;

/**
 * Sets an uncaught exception handler on its own thread, then fails. The handler hands the exception over through a
 * system property, which is visible from any class loader.
 */
public class ThreadHandlerMain {
    public static final String SEEN_PROPERTY = "exec.test.uncaught.seen";

    public static void main(String... args) {
        Thread.currentThread()
                .setUncaughtExceptionHandler(
                        (thread, throwable) -> System.getProperties().put(SEEN_PROPERTY, throwable));
        throw new IllegalStateException("expected exception from ThreadHandlerMain");
    }
}
