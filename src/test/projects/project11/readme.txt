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
[DEBUG] Adding to classpath : file:/home/jerome/Projects/OSS/m2/mojo-https/trunk/mojo/exec-maven-plugin/src/test/projects/project11/target/classes/
[DEBUG] Adding to classpath : file:/home/jerome/Projects/OSS/m2/mojo-https/trunk/mojo/exec-maven-plugin/src/test/projects/project11/target/test-classes
[DEBUG] Adding project dependency artifact: commons-lang to classpath
[DEBUG] Adding project dependency artifact: tools to classpath
[DEBUG] joining on thread Thread[org.codehaus.mojo.exec.FindClassInClasspath.main(),5,org.codehaus.mojo.exec.FindClassInClasspath]
[DEBUG] Setting accessibility to true in order to invoke main().
OK
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESSFUL
[INFO] ------------------------------------------------------------------------

