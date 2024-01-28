File log = new File(basedir, 'build.log')

assert log.exists()
assert log.getText().contains( "[INFO] Toolchain in exec-maven-plugin: Paths[scripts]" )
assert log.getText().contains( "Hello from exec-maven-plugin" )