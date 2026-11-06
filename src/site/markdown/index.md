title: Introduction
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

# Exec Maven Plugin

The plugin provides 2 goals to help execute system and Java programs.

## Goals Overview

General information about the goals.

* [exec:exec](./exec-mojo.html) execute programs and Java programs in a separate process.
* [exec:java](./java-mojo.html) execute Java programs in the same VM.

## Usage

General instructions on how to use the Exec Maven Plugin can be found on the [usage page](./usage.html).

In case you still have questions regarding the plugin's usage, please feel
free to contact the [user mailing list](./mailing-lists.html). The posts to the mailing list are archived and could
already contain the answer to your question as part of an older thread. Hence, it is also worth browsing/searching
the [mail archive](./mailing-lists.html).

If you feel like the plugin is missing a feature or has a defect, you can fill a feature request or bug report in our
[issue tracker](./issue-management.html).

When creating a new issue, please provide a comprehensive description of your
concern. Especially for fixing bugs it is crucial that the developers can reproduce your problem. For this reason,
entire debug logs, POMs or most preferably little demo projects attached to the issue are very much appreciated.
Of course, patches are most welcome too.

Contributors can check out the project from our [source repository](./scm.html) and will find supplementary
information in the [Reporting an Issue](https://www.mojohaus.org/contribution/reporting-an-issue.html),
[Submitting a Patch](https://www.mojohaus.org/contribution/submitting-a-patch.html)

## Examples

To provide you with better understanding of some usages of the Exec Maven Plugin,
you can take a look into the following examples:

* [Running Java programs with exec:exec](./examples/example-exec-for-java-programs.html)
* [Changing the classpath scope when running Java programs](./examples/example-exec-or-java-change-classpath-scope.html)
* [Using plugin dependencies with exec:exec](./examples/example-exec-using-plugin-dependencies.html)
* [Using toolchains instead of explicit paths](./examples/example-exec-using-toolchains.html)
* [Using executable binary dependencies instead of local executables](./examples/example-exec-using-executabledependency.html)
* [Forward maven system properties to the main](./examples/example-java-project-properties.html)
* [Execute a Runnable instead of a main](./examples/example-java-runnable.html)

