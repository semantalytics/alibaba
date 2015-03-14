@echo off

rem Set the lib dir relative to the batch file's directory
set LIB_DIR=%~dp0\..\lib
set DIST_DIR=%~dp0\..\dist
set LOG_CONF=%~dp0\logging.properties
rem echo LIB_DIR = %LIB_DIR%
rem echo DIST_DIR = %DIST_DIR%
rem echo LOG_CONF = %LOG_CONF%

rem Slurp the command line arguments. This loop allows for an unlimited number
rem of arguments (up to the command line limit, anyway).
set CMD_LINE_ARGS=%1
if ""%1""=="""" goto setupArgsEnd
shift
:setupArgs
if ""%1""=="""" goto setupArgsEnd
set CMD_LINE_ARGS=%CMD_LINE_ARGS% %1
shift
goto setupArgs

:setupArgsEnd

if "%JAVA_HOME%" == "" goto noJavaHome
if not exist "%JAVA_HOME%\bin\java.exe" goto noJavaHome
goto javaHome

:noJavaHome
set JAVA=java
goto javaHomeEnd

:javaHome
set JAVA=%JAVA_HOME%\bin\java

:javaHomeEnd

rem use java 6 wildcard feature
rem echo Using wildcard to set classpath
"%JAVA%" -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "%LIB_DIR%\*;%DIST_DIR%\*" org.openrdf.http.object.ServerControl %CMD_LINE_ARGS%
goto end

:end
