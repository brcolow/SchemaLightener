@echo off

pushd "%~dp0"
call mvnw.cmd -q -DskipTests exec:java
popd
