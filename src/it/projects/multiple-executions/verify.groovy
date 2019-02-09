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
def getMavenVersion(buildLog) {
    def maven = null;
    buildLog.eachLine { line ->
        if (line.startsWith("Apache Maven 2.0.11")) {
            maven = "2.0.11";
        } else if (line.startsWith("Apache Maven 2.2.1")) {
            maven = "2.2.1";
        } else if (line.startsWith("Apache Maven 3.0.3")) {
            maven = "3.0.3";
        } else if (line.startsWith("Apache Maven 3.0.4")) {
            maven = "3.0.4";
        } else if (line.startsWith("Apache Maven 3.0.5")) {
            maven = "3.0.5";
        } else if (line.startsWith("Apache Maven 3.1.0")) {
            maven = "3.1.0";
        } else if (line.startsWith("Apache Maven 3.1.1")) {
            maven = "3.1.1";
        } else if (line.startsWith("Apache Maven 3.2.1")) {
            maven = "3.2.1";
        }
    }

    return maven
}

def mavenVersion = getMavenVersion(buildLog)

   
def projectVersion = getProjectVersion();
def pluginVersion = getPluginVersion();

println "Project version: ${projectVersion}"
println "Plugin version ${pluginVersion}"

if (mavenVersion.equals("2.0.11") || mavenVersion.equals("2.2.1")) {
  t.checkExistenceAndContentOfAFile(buildLog, [
    "[DEBUG]   (f) arguments = [-cp, target/classes, Main]",
    "[INFO] [exec:exec {execution: first-execution}]",
    "[INFO] [exec:exec {execution: second-execution}]",
    "[INFO] [exec:exec {execution: third-execution}]",
  ])
} else {
  t.checkExistenceAndContentOfAFile(buildLog, [
    "[DEBUG]   (f) arguments = [-cp, target/classes, Main]",
    "[INFO] --- exec-maven-plugin:" + pluginVersion + ":exec (first-execution) @ multiple-execution ---",
    "[INFO] --- exec-maven-plugin:" + pluginVersion + ":exec (second-execution) @ multiple-execution ---",
    "[INFO] --- exec-maven-plugin:" + pluginVersion + ":exec (third-execution) @ multiple-execution ---",
  ])
}
