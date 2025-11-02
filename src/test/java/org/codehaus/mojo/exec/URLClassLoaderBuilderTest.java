package org.codehaus.mojo.exec;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Basic tests about the custom classloader we set to execute the project.
 */
class URLClassLoaderBuilderTest {
    @Test
    void childFirst() throws Exception {
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalStderr = System.err;
        Thread thread = Thread.currentThread();
        ClassLoader testLoader = thread.getContextClassLoader();
        try (URLClassLoader loader = URLClassLoaderBuilder.builder()
                        .setPaths(asList(
                                Paths.get("target/test-dependencies/slf4j-api.jar"),
                                Paths.get("target/test-dependencies/slf4j-jdk14.jar")))
                        .build();
                PrintStream tmpStderr = new PrintStream(stderr)) {
            System.setErr(tmpStderr);
            assertEquals(System.err, tmpStderr);
            thread.setContextClassLoader(loader);
            Class<?> lf = loader.loadClass("org.slf4j.LoggerFactory");
            Object logger = lf.getMethod("getLogger", Class.class).invoke(null, String.class);
            assertEquals("org.slf4j.impl.JDK14LoggerAdapter", logger.getClass().getName());
        } finally {
            thread.setContextClassLoader(testLoader);
        }
        assertEquals("", new String(stderr.toByteArray(), StandardCharsets.UTF_8));
        System.setErr(originalStderr);
    }
}
