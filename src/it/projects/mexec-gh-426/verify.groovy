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

// Verify that the build log contains the expected output from ServiceLoader
File buildLog = new File(basedir, 'build.log')
assert buildLog.exists()

String log = buildLog.getText('UTF-8')

// Check that exec:java ran successfully
assert log.contains('[INFO] --- exec')
assert log.contains(':java')

// Check that ServiceLoader found the provider and printed the greeting
assert log.contains('Hello from ServiceLoader, World!')

// Ensure no errors about missing service providers
assert !log.contains('No Greeter service provider found')

println "SUCCESS: JPMS ServiceLoader test passed - provider was loaded and executed correctly"
