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

def buildLogLines = new File( basedir, "build.log" ).readLines()

// Find "System::exit was called" line index
def infoMessageLineNumber = buildLogLines.indexOf("[INFO] System::exit was called with return code 123")
assert infoMessageLineNumber > 0
// Verify that preceding line is program output
assert buildLogLines[infoMessageLineNumber - 1] == "[one, two, three]"
// Verify that subsequent lines contain the beginning of the thrown SystemExitException stack trace
assert buildLogLines[infoMessageLineNumber + 1].startsWith("[WARNING]")
assert buildLogLines[infoMessageLineNumber + 2].contains("SystemExitException: System::exit was called with return code 123")
assert buildLogLines[infoMessageLineNumber + 3].contains("SystemExitManager.exit (SystemExitManager.java")
