@echo off
setlocal

set binDir=%~dp0

if exist "%binDir%\control.jar" goto customControl
set controlJar=%binDir%\ctrlbase.jar
goto fi
:customControl
set controlJar=%binDir%\control.jar
:fi

if "%1"=="-c" goto consoleMode
start "" /b javaw -jar "%controlJar%" %*
goto fi2
:consoleMode
java -jar "%controlJar%" %*
:fi2

