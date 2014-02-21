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

import java.io.*
import java.util.*

t = new IntegrationBase()
 
def buildLog = new File( basedir, "build.log" )
 
def getPluginVersion() {
    def pom = new XmlSlurper().parse(new File(basedir, 'pom.xml'))
   
      def allPlugins = pom.build.plugins.plugin;
   
      def configurationMavenPlugin = allPlugins.find {
          item -> item.groupId.equals("org.codehaus.mojo") && item.artifactId.equals("exec-maven-plugin");
      }
      
      return configurationMavenPlugin.version;
}

def getProjectVersion() {
    def pom = new XmlSlurper().parse(new File(basedir, 'pom.xml'))
   
      def allPlugins = pom.version;
   
      return pom.version;
}
   
def projectVersion = getProjectVersion();
def pluginVersion = getPluginVersion();

println "Project version: ${projectVersion}"
println "Plugin version ${pluginVersion}"
   
t.checkExistenceAndContentOfAFile(buildLog, [
  "[DEBUG]   (f) failWithEmptyArgument = true",
  "[DEBUG]   (f) arguments = [-cp, target/classes, Main]",
  "[INFO] --- exec-maven-plugin:" + pluginVersion + ":exec (first-execution) @ multiple-execution ---",
  "[INFO] --- exec-maven-plugin:" + pluginVersion + ":exec (second-execution) @ multiple-execution ---",
  "[INFO] --- exec-maven-plugin:" + pluginVersion + ":exec (third-execution) @ multiple-execution ---",
])
