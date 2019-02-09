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


// The problem of this integration test was that
// it is not predictable to know if everything
// is written continuously into the file one
// line after the other.
// Based on the async behaviour it could happen
// that a new line is written earlier than
// some content from one other stream (stderr/stdout).
//
// So i had to find a way to ignore the above.
// The simplest solution was to just
// find the content line like "1. System.out line"
// which means the string of bytes which represents
// the line but not the ending NEW LINE (0x0a).
//
// The assumption in that IT is that
// the line "1. System.out line" is written
// continuously without any break in it.
// Otherwise this test will fail.

import org.codehaus.plexus.util.FileUtils
import org.codehaus.plexus.util.IOUtil

import java.io.*
import java.util.*

import static junit.framework.Assert.assertEquals
import static junit.framework.Assert.assertTrue

private int countTheNumberOfLines(String expectedHexString, String line) {
    int numberOfLine = 0;

    boolean found = true;
    while (found) {
        int posOfFirstLine = expectedHexString.indexOf(line);
        if (posOfFirstLine >= 0) {
            found = true;
            numberOfLine++;
            // line.length():
            //     +2 for hex means two character like 0a
            //     +1 for space during the hex string generation.
            // depends on the platform.
            expectedHexString = expectedHexString.substring(posOfFirstLine+line.length());
        }  else {
            found = false;
        }
    }

    return numberOfLine;
}

private String convertStringToHex(String expectedOutput) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < expectedOutput.length(); i++) {
        byte c = (byte) expectedOutput.charAt(i);
        result.append(String.format("%02x", c));
        result.append(' ');
    }
    return result.toString();
}


def outLog = new File(basedir, 'target/out.log')

if (!outLog.exists()) {
    throw new FileNotFoundException( "Could not find the " + outLog );
}

def expectedOutput = FileUtils.fileRead(outLog)

def expectedHexString = convertStringToHex(expectedOutput);

def expectedLines = [
    "1. System.out line",
    "2. System.err line",
    "3. System.out line",
    "4. System.err line",
    "5. System.out line",
]

for (int i = 0; i < expectedLines.size(); i++) {
    def expectedLine = convertStringToHex(expectedLines[i]);
    assertTrue(expectedHexString.contains((expectedLine)));
}

def LINE = System.getProperty("line.separator");
def LineInHex = convertStringToHex(LINE);

def numberOfLine = countTheNumberOfLines(expectedHexString, LineInHex);
assertEquals(5, numberOfLine);
