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
File log = new File(basedir, 'build.log')
assert log.exists()

String expectedClasspath = new File(basedir, 'target/classes').toURI().toURL().toString()
assert log.getText().contains( "Adding to classpath : $expectedClasspath" )

expectedClasspath = new File(basedir, 'etc/run1').toURI().toURL().toString()
assert log.getText().contains( "Adding additional classpath element: $expectedClasspath" )

expectedClasspath = new File(basedir, 'etc/run2').toURI().toURL().toString()
assert log.getText().contains( "Adding additional classpath element: $expectedClasspath" )

//expectedClasspath = new File("c:\\windows\\system32\\drivers\\etc").toURI().toURL().toString()
//assert log.getText().contains( "Adding additional classpath element: $expectedClasspath" )


//expectedClasspath = "customdir-before${File.pathSeparator}${new File(basedir, 'target/classes').getAbsolutePath()}"
//assert log.getText().contains( "java -classpath $expectedClasspath Main" )

//expectedClasspath = "${new File(basedir, 'target/classes').getAbsolutePath()}${File.pathSeparator}customdir-after"
//assert log.getText().contains( "java -classpath $expectedClasspath Main" )

//expectedClasspath = "customdir-before${File.pathSeparator}${new File(basedir, 'target/classes').getAbsolutePath()}${File.pathSeparator}customdir-after"
//assert log.getText().contains( "java -classpath $expectedClasspath Main" )