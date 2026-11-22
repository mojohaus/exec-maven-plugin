title: Usage
author: Jerome Lacoste
date: 2008-10-10

<!--
Copyright 2006 The Codehaus

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

Usage
=====

Exec goal
---------

You can formally specify all the relevant execution information in the plugin configuration.
Depending on your use case, you can also specify some or all information using system properties.

### Command line

Using system properties you would just execute it like in the following example.

```sh
mvn exec:exec -Dexec.executable="maven" [-Dexec.workingdir="/tmp"] -Dexec.args="-X myproject:dist"
```

### POM Configuration

Add a configuration similar to the following to your POM:

```xml
<project>
  ...
  <build>
    <plugins>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version><!-- latest release --></version>
        <executions>
          <execution>
            ...
            <goals>
              <goal>exec</goal>
            </goals>
          </execution>
        </executions>
        <configuration>
          <executable>maven</executable>
          <!-- optional -->
          <workingDirectory>/tmp</workingDirectory>
          <arguments>
            <argument>-X</argument>
            <argument>myproject:dist</argument>
            ...
          </arguments>
          <environmentVariables>
            <LANG>en_US</LANG>
          </environmentVariables>
        </configuration>
      </plugin>
    </plugins>
  </build>
   ...
</project>
```

Java goal
---------

This goal helps you run a Java program within the same VM as Maven.

### Differences compared to plain command line

The goal goes to great length to try to mimic the way the VM works, but there are some subtle differences.
Today all differences come from the way the goal deals with **thread management**.

|                             command line                              |                                                                                                                                            Java Mojo                                                                                                                                             |
|-----------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| The VM exits as soon as the only remaining threads are daemon threads | By default daemon threads are joined and interrupted once all known non daemon threads have quit. The join timeout is customisable. The user might wish to further cleanup by stopping the unresponsive threads. The user can disable the full extra thread management (interrupt/join/\[stop\]) |

#### Important limitation: Thread Group Isolation

The java goal tracks threads created within its own **isolated thread group**.
It does **not** track threads created in other thread groups.
This means that if your code creates threads outside the isolated thread group
(for example, through RMI, JMX, or other frameworks), those threads will not be
detected as "known" threads, and the plugin may exit even if such non-daemon threads are still running.

**Examples of affected scenarios:**

* **RMI** - `java.rmi.registry.LocateRegistry.createRegistry()` creates non-daemon threads
  in the system thread group, not in the plugin's isolated thread group.
* **JMX** - Similar to RMI, JMX remote connectors may create threads outside the isolated group.
* **Other frameworks** that manage their own thread pools or create threads using
  the system thread group.

In such cases, the plugin will exit when all threads in **its own thread group** have completed,
even though non-daemon threads may still be running in other thread groups. This can cause
servers or services to be terminated prematurely.

**Workaround:** If you need threads created by frameworks like RMI to keep the JVM alive,
use the [exec goal](./exec-mojo.html) to run your Java program in a separate JVM process instead.
The exec goal spawns a new process which will behave like running `java` from the command line.

Read the documentation for the [java goal](./java-mojo.html) for more information on how to configure this behavior.

If you find out that these differences are unacceptable for your case, you may need to use the [exec goal](./exec-mojo.html) to wrap your Java executable.

### Command line

If you want to execute Java programs in the same VM, you can either use the command line version

```sh
mvn exec:java -Dexec.mainClass="com.example.Main" [-Dexec.args="argument1"] ...
```

### POM Configuration

or you can configure the plugin in your POM:

```xml
<project>
  ...
  <build>
    <plugins>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <executions>
          <execution>
            ...
            <goals>
              <goal>java</goal>
            </goals>
          </execution>
        </executions>
        <configuration>
          <mainClass>com.example.Main</mainClass>
          <arguments>
            <argument>argument1</argument>
            ...
          </arguments>
          <systemProperties>
            <systemProperty>
              <key>myproperty</key>
              <value>myvalue</value>
            </systemProperty>
            ...
          </systemProperties>
        </configuration>
      </plugin>
    </plugins>
  </build>
   ...
</project>
```

**Note:** The `java` goal doesn't spawn a new process. Any VM specific option that you want to pass to the executed class must be passed to the Maven VM using
the `MAVEN_OPTS` environment variable.
E.g.

```sh
MAVEN_OPTS=-Xmx1024m
```

Otherwise consider using the `exec` goal.

By default the class loader that runs the main class is closed once the program is done, so shutdown hooks and
daemon threads that load a class afterwards fail with a `NoClassDefFoundError`. Since 3.7.0, set
`keepClassLoaderOpen` to `true` to keep it open. An open class loader and its classes stay in memory for as long
as a thread or hook references them, which matters in a long-lived Maven process such as mvnd.

### Running a Java program in the background

Since 3.8.0, `exec:java` can start a program and let the build go on while it runs, for example a server that
integration tests talk to. Set `async` to `true`, tell the plugin how to see that the program is ready, and bind
`java-stop` to the phase after the tests:

```xml
<plugin>
  <groupId>org.codehaus.mojo</groupId>
  <artifactId>exec-maven-plugin</artifactId>
  <executions>
    <execution>
      <id>start-server</id>
      <phase>pre-integration-test</phase>
      <goals>
        <goal>java</goal>
      </goals>
      <configuration>
        <mainClass>com.example.Server</mainClass>
        <arguments>
          <argument>8081</argument>
        </arguments>
        <async>true</async>
        <asyncStartCheck>http://localhost:8081/health</asyncStartCheck>
        <asyncStartTimeout>30000</asyncStartTimeout>
      </configuration>
    </execution>
    <execution>
      <id>stop-server</id>
      <phase>post-integration-test</phase>
      <goals>
        <goal>java-stop</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

The program has three steps in the build:

* **Start.** The goal launches the program and returns as soon as `asyncStartCheck` succeeds. The check is
  `tcp://host:port` (a connection opens), an `http://` or `https://` URL (a GET request answers with a 2xx status), or
  a script when `asyncStartCheckScriptLanguage` names a JSR-223 language such as `groovy`. The JDK has no script engine
  since Java 15, so add one as a dependency of the plugin and there is no default language. Without a check the goal
  returns at once. The goal waits at most `asyncStartTimeout` milliseconds, polling every
  `asyncStartCheckPollInterval`, and fails, after stopping the program, when the time is up, when the program throws,
  or when it ends before it is ready. The address checks use the proxy settings of the JVM, not those of
  `settings.xml`.
* **Run.** Later executions in the same build use the program. System properties set with `systemProperties` stay set
  until the program stops, and they are those of the whole Maven JVM.
* **Stop.** `exec:java-stop` interrupts every thread of the program, waits for them at most
  `daemonThreadJoinTimeout` milliseconds (15000 if that is not positive), closes the class loader unless
  `keepClassLoaderOpen` is set, and restores the system properties that the program set with `systemProperties`, each
  one only if it still has the value the program gave it, so other programs and mojos keep what they set meanwhile. It fails the build if the program threw an
  exception, whenever that happened. `startExecutionId` stops only the program of that execution. With no program to stop, or no program under that id
  because `async` or the start was skipped, the goal logs it and succeeds.

The program should end when its thread is interrupted, which is what a server does that waits for its shutdown. A
thread that ignores the interruption lingers and is reported in the log, as it is without `async`.

A program is identified by the id of its execution, so two executions in one project that run in the background need
different ids (`default-cli` on the command line). A program belongs to its project: a parallel build (`-T`) stops each
module's program in that module, and a goal that runs in another module does not see it.

If the build ends without `java-stop`, for example because a test failed before `post-integration-test`, a shutdown
hook interrupts the program and waits for its threads at most `daemonThreadJoinTimeout` milliseconds (15 seconds
when that is 0 or less), so Maven still exits. In a long-lived Maven process such as mvnd the hook runs only when that
process ends, so the program, and the port it holds, stay up until `mvnd --stop`.

`exec.async` is the property of `exec:exec`. The `java` goal uses `exec.java.async` instead, so that setting
`-Dexec.async=true` for a forked process does not move every `exec:java` execution into the background, where it needs
the stop goal. The program may still call `System.exit`, which ends the whole build, unless `blockSystemExit` is set. A
shutdown hook that the program registers runs when Maven exits, after the class loader is closed, so set
`keepClassLoaderOpen` if that hook loads classes.
