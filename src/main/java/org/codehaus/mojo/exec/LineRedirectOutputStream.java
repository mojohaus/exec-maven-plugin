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

import java.io.OutputStream;
import java.util.function.Consumer;

/**
 * An output stream that captures one line of output at a time, and then
 * redirects that line to some {@link Consumer} to act upon as it pleases. This
 * class is not thread safe and expects to have only one active writer consuming
 * it at any given time.
 * 
 * @since 3.0.0
 */
class LineRedirectOutputStream extends OutputStream {

    private StringBuilder currentLine = new StringBuilder();
    private final Consumer<String> linePrinter;

    public LineRedirectOutputStream(Consumer<String> linePrinter) {
        this.linePrinter = linePrinter;
    }

    @Override
    public void write(final int b) {
        if ((char) b == '\n') {
            printAndReset();
            return;
        }
        currentLine.append((char) b);
    }

    @Override
    public void flush() {
        if (currentLine.length() > 0) {
            printAndReset();
        }
    }

    private void printAndReset() {
        linePrinter.accept(currentLine.toString());
        currentLine = new StringBuilder();
    }
}
