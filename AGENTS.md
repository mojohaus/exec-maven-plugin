# Agent guide for exec-maven-plugin

This file is for automated agents and human contributors working on this repository. It records
what the code and the POMs do not make obvious.

## Layout

- One module with the goals `exec` (`ExecMojo`, runs a program in a forked process) and `java`
  (`ExecJavaMojo`, runs a main class in the Maven JVM), in `org.codehaus.mojo.exec`.
  `AbstractExecMojo` and `AbstractExecJavaBase` hold what they share.
- `src/main/java9` holds Java 9+ code. The `java9-mrjar` profile (JDK 9 and later) compiles it with
  `release` 9 into `META-INF/versions/9` of the jar, so the jar is multi-release.
- The site is built from `src/site`.

## Build gates

- Main code in `src/main/java` must use the Java 8 API: mojo-parent sets `maven.compiler.release`
  to 8.
- Spotless runs on JDK 17 and later only. It applies the formatting in `process-sources` on a local
  build and checks it when the `CI` environment variable is set. It formats Java with
  palantir-java-format and also reformats Markdown, so run `mvn spotless:apply` after editing the
  README or this file.
- The plugin compiles against Maven 3.9.16 (`mavenVersion`) and declares a literal
  `<prerequisites><maven>3.9.0</maven>`; keep the two apart, so raising `mavenVersion` does not
  raise the prerequisite. Building the plugin itself needs Maven 3.9 (`minimalMavenBuildVersion`).
- `.github/workflows/maven.yml` calls the shared `apache/maven-gh-actions-shared`
  `maven-verify.yml@v5` workflow. Branches in this repository build under the push event.

## Unit tests

- JUnit 5 and Mockito. `ExecMojoTest` and `ExecJavaMojoTest` use `@MojoTest` and `@InjectMojo` from
  `maven-plugin-testing-harness`; tests that need a project take its POM from `src/test/projects`
  with `@InjectMojo(pom = ...)`, and the Maven components the mojo needs are Mockito mocks.
- Prefer a unit test. Add an integration test only for what needs a real Maven build: toolchains,
  module path handling, the project classpath or dependency resolution.

## Integration tests

- They live in `src/it/projects`, one directory per test, and run through the `run-its` profile. A
  mock repository (`mrm-maven-plugin`) serves `src/it-repo` to them through `src/it/settings.xml`;
  the invoker plugin installs the plugin into `target/local-repo`.
- The build fails unless the `JAVA_HOME` environment variable is set. Run a subset:

  ```shell
  JAVA_HOME=/path/to/jdk mvn -B -P run-its verify -Dinvoker.test=setup-parent,mexec-92
  ```

  `-Dinvoker.test` filters the `setup-parent` project too, so list it with the tests that use it.

- They run in parallel, one per CPU core (`invoker.parallelThreads` 1C), so a test must not depend
  on another test's output.

- The forked builds get `-XX:TieredStopAtLevel=1 -XX:+UseSerialGC` through `mavenOpts`, not through
  `JAVA_TOOL_OPTIONS`, which the JVMs that the tests start would report on stderr.

- Write `verify.groovy` as `assert` statements. Invoker fails a script whose last expression is
  anything other than `true` or `null`, so a trailing `list.each { }` fails the test; use a `for`
  loop.

## Dependencies

- Dependabot excludes `src/it`, `src/test/projects`, `src/it-repo` and `src/test/resources` from
  version updates (`exclude-paths`), so the fixtures keep their old versions on purpose. Security
  updates can still propose them; close those pull requests.
- Deliberate pins in `.github/dependabot.yml`: Maven artifacts stay below 3.10.0, the Maven 3.9.x
  line being the plugin baseline; Maven Resolver below 2.0 and plexus-xml below 4.0.0 because they
  target Maven 4; SLF4J below 2.0 because 2.x needs an `SLF4JServiceProvider` that the Maven 3
  runtime does not provide; Mockito below 5 because it needs Java 11 and the build still runs on
  JDK 8.

## Pull requests

- Pull requests are squash- or rebase-merged; merge commits are disabled. Release Drafter builds
  the release notes from the titles and labels, so the title should describe the change for a
  user.

