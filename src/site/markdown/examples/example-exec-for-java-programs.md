title: Running Java programs with the exec goal
author: Jerome Lacoste
date: 2008-10-10

<!--
Copyright 2008 The Codehaus

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

# Running Java programs with the exec goal

Special treatment of some command line arguments and configuration parameters facilitate the running of Java programs in external processes.

Note: With the `exec` goal, there are some confusion caused by the almost duplication of functionality between the `arguments` and
`commandlineArgs` configuration parameters.

* If `commandlineArgs` is specified, it will be used as is, except for replacing %classpath and %modulepath with their matching path using dependencies
* Otherwise if the property `exec.args` is specified, it will be used
* Otherwise the list of `<argument>`, `<classpath>` and `<modulepath>` will be parsed and used

## Command line

If specified as part of the `exec.args` argument, the special string `%classpath`
will be replaced by the project classpath as computed by Maven. Same counts for `%modulepath`

```sh
mvn exec:exec -Dexec.executable="java" [...] -Dexec.args="%classpath"
```

## POM configuration

To execute Java programs, the Exec Maven Plugin helps by allowing the `<classpath>` special argument:

```xml
<configuration>
  <executable>java</executable>
  <arguments>
    <argument>-classpath</argument>
    <!-- automatically creates the classpath using all project dependencies,
         also adding the project build directory -->
    <classpath/>
    <argument>com.example.Main</argument>
    ...
  </arguments>
</configuration>
```

or if one wants to restrict the dependencies in the classpath:

```xml
<configuration>
  <executable>java</executable>
  <arguments>
    <argument>-classpath</argument>
    <classpath>
      <dependency>commons-io:commons-io</dependency>
      <dependency>commons-lang:commons-lang</dependency>
    </classpath>
    <argument>com.example.Main</argument>
    ...
  </arguments>
</configuration>
```

In case of the modules as supported since Java9 the configuration looks like

```xml
<configuration>
  <executable>java</executable>
  <arguments>
    <argument>--module-path</argument> <!-- or -p  -->
    <!-- automatically creates the modulepath using all project dependencies,
         also adding the project build directory -->
    <modulepath/>
    <argument>--module</argument> <!-- or -m -->
    <argument>mymodule/com.example.Main</argument>
    ...
  </arguments>
</configuration>
```

### The module path goes into an argument file

When the executable is `java` (or `forceJava` is set), the plugin does not put `--module-path` or `-p` and the
path on the command line. It writes both to the file `modulepath` in the build directory and passes `@<build directory>/modulepath`
instead, so that a long module path does not exceed the command line length limit of the operating system. `java` reads
the options from that file, so the program runs as if you had written them out. With the configuration above, `mvn -X exec:exec`
shows a command line like this one:

```
java @/path/to/project/target/modulepath --module mymodule/com.example.Main
```

Neither `--module-path` nor the path appears in it, and that is expected.

To get the two arguments on the command line instead, set `longModulepath` to `false`, for example with `-Dexec.longModulepath=false`:

```xml
<configuration>
  <executable>java</executable>
  <longModulepath>false</longModulepath>
  ...
</configuration>
```

Use this setting also when the executable is not `java` but a launcher that does not understand `@` argument files.
Only the pair of `--module-path` (or `-p`) followed by `<modulepath/>` is rewritten: a `<modulepath/>` without that option before it is replaced by the bare path.
