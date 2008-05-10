This test cannot yet be automated. To test using mvn:

mkdir -p target
rm -rf target/classes
cp -ar ../../../../target/test-classes target/classes
mvn -X test

[...]
[INFO] [exec:java {execution: default}]
[DEBUG] Invoking : org.codehaus.mojo.exec.FindClassInClasspath.main(sun.tools.javac.Main)
[DEBUG] Plugin Dependencies will be excluded.
[DEBUG] Project Dependencies will be included.
[DEBUG] Collected project artifacts [com.sun:tools:jar:1.5:system, commons-lang:commons-lang:jar:1.0.1:compile]
[DEBUG] Collected project classpath [/home/jerome/Code/OSS/m2/exec-maven-plugin/src/test/projects/project11/target/classes]
[DEBUG] Adding to classpath : file:/home/jerome/Code/OSS/m2/exec-maven-plugin/src/test/projects/project11/target/classes/
[DEBUG] Adding project dependency artifact: tools to classpath
[DEBUG] Adding project dependency artifact: commons-lang to classpath
[DEBUG] joining on thread Thread[org.codehaus.mojo.exec.FindClassInClasspath.main(),5,org.codehaus.mojo.exec.FindClassInClasspath]
[DEBUG] Setting accessibility to true in order to invoke main().
OK
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESSFUL
[INFO] ------------------------------------------------------------------------

Expectation: both commons-lang and tools are added to the classpath

