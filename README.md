# MojoHaus Exec Maven Plugin

This is the [exec-maven-plugin](http://www.mojohaus.org/exec-maven-plugin/).

[![Apache License, Version 2.0, January 2004](https://img.shields.io/github/license/mojohaus/sql-maven-plugin.svg?label=License)](http://www.apache.org/licenses/)
[![Maven Central](https://img.shields.io/maven-central/v/org.codehaus.mojo/exec-maven-plugin.svg?label=Maven%20Central)](https://search.maven.org/artifact/org.codehaus.mojo/exec-maven-plugin)
[![GitHub CI](https://github.com/mojohaus/exec-maven-plugin/actions/workflows/maven.yml/badge.svg)](https://github.com/mojohaus/exec-maven-plugin/actions/workflows/maven.yml)

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

