
@setlocal
@for /F "delims=." %%v in ('echo') do @set echo=%%v
@for %%v in (%echo%) do @set echo=%%v
@echo off

REM Wrapper script for running Qizx in command-line mode
REM  (c) Axyana software 2007-2010
REM

REM dbin is the location of this script (qizx)
REM (%~dp0 is expanded pathname of the current script under NT).

set dbin=%~dp0
set distrib=%dbin%..
set dlib=%distrib%\lib

REM
REM       Basic jars
REM
set jars=%dlib%\qizx.jar;%dlib%\jhall.jar;%dlib%\resolver.jar;%dlib%\qizx_eval_key.jar

REM       Custom jars can be added here:
REM
REM set jars=%jars%;myjar.jar

REM
REM       Memory size can be customized here (Mb)
set VMOPTS=-Xmx200m %VMOPTS%

REM     %distrib%/config added to classpath to use XML catalogs:
REM
java -classpath "%distrib%\config;%distrib%\build;%jars%" -Dxml.catalog.files="%XML_CATALOG_FILES%" %VMOPTS% com.qizx.apps.QizxCLI %*

@endlocal

