@echo off

SETLOCAL ENABLEDELAYEDEXPANSION

CALL "%~dp0common.bat"

if %errorlevel% neq 0 (
    exit /b %errorlevel%
)

echo "%RUN_JAVA%" --enable-native-access=ALL-UNNAMED %displayOpts% -cp %CLASSPATH% com.hazelcast.client.console.HazelcastCommandLine %*
"%RUN_JAVA%" --enable-native-access=ALL-UNNAMED %filteredOpts% -cp %CLASSPATH% com.hazelcast.client.console.HazelcastCommandLine %*

ENDLOCAL
