# MojoHaus Exec Maven Plugin

[![Apache License, Version 2.0, January 2004](https://img.shields.io/github/license/mojohaus/exec-maven-plugin.svg?label=License)](https://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/org.codehaus.mojo/exec-maven-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/org.codehaus.mojo/exec-maven-plugin)
[![GitHub CI](https://github.com/mojohaus/exec-maven-plugin/actions/workflows/maven.yml/badge.svg?branch=master)](https://github.com/mojohaus/exec-maven-plugin/actions/workflows/maven.yml?query=branch%3Amaster)

The Exec Maven Plugin runs system programs and Java programs from a build: `exec:exec` starts a
program in a separate process, and `exec:java` runs a main class in the Maven JVM. It requires
Maven 3.9.0 or later and Java 8 or later.

```sh
mvn exec:java -Dexec.mainClass="com.example.Main"
```

The [plugin site](https://www.mojohaus.org/exec-maven-plugin/) lists the goals and has a
[usage page](https://www.mojohaus.org/exec-maven-plugin/usage.html) and examples. Contributor and
agent notes are in [AGENTS.md](AGENTS.md).

## Running integration tests

Execute

```sh
mvn -P run-its clean verify
```

Running a single test:

```sh
mvn -P run-its clean verify "-Dinvoker.test=setup-parent,<TEST-PROJECT-NAME>"
```

## Releasing

* Make sure `gpg-agent` is running.
* Execute `mvn -B release:prepare release:perform`

For publishing the site do the following:

```
cd target/checkout
mvn verify site site:stage scm-publish:publish-scm
```

