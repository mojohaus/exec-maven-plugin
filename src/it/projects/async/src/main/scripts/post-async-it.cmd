@echo off
rem Wait for just long enough for the async-it.cmd script to start up and emit its verify message
rem Can't use Windows timeout command as it throws ERROR: Input redirection is not supported, exiting the process immediately.
rem timeout 1 /nobreak > nul
waitfor /T 2 xxx > nul
echo post-async-it
