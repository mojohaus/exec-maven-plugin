@echo off
echo async exec:exec test
rem Block, so that we're still executing when the Maven JVM shuts down and destroys our shell process
pause > nul
echo async exec:exec post-test
