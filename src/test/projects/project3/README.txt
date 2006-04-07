the target/exec/output.txt should contain only 3 elements:
- the classpath with 2 elements: the commons-io and commons-logging jars + the classes, but not the commons-lang
- a passed argument (project.env1)
- the project user.dir, matching $PWD/target
