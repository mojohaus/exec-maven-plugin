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

// Second-last line is the last line the called program prints before exiting the JVM with System.exit.
// Last line is "Running post-build script: ...", i.e. we need to disregard it.
assert buildLogLines[-2] == "[one, two, three]"
