@REM
@REM  Copyright 2005-2018 Red Hat, Inc.
@REM
@REM  Red Hat licenses this file to you under the Apache License, version
@REM  2.0 (the "License"); you may not use this file except in compliance
@REM  with the License.  You may obtain a copy of the License at
@REM
@REM     http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM  Unless required by applicable law or agreed to in writing, software
@REM  distributed under the License is distributed on an "AS IS" BASIS,
@REM  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
@REM  implied.  See the License for the specific language governing
@REM  permissions and limitations under the License.
@REM

@echo off

if not "%ECHO%" == "" echo %ECHO%

setlocal
set DIRNAME=%~dp0%

if "%KARAF_PROMETHEUS_VERSION%" == "" goto :SET_VERSION
	goto :SET_VERSION_END
:SET_VERSION
	set KARAF_PROMETHEUS_VERSION=0.13.0
:SET_VERSION_END

if "%KARAF_PROMETHEUS_PORT%" == "" goto :SET_PORT
	goto :SET_PORT_END
:SET_PORT
	set KARAF_PROMETHEUS_PORT=9779
:SET_PORT_END

set KARAF_HOME=%DIRNAME%..
set KARAF_PROMETHEUS_CONFIG=%KARAF_HOME%/etc/prometheus-config.yml
if exist "%KARAF_PROMETHEUS_CONFIG%" goto :SET_JAVA_EXTRA_OPTS
	goto :SET_JAVA_EXTRA_OPTS_END

:SET_JAVA_EXTRA_OPTS
	set EXTRA_JAVA_OPTS=-javaagent:%KARAF_HOME%/lib/jmx_prometheus_javaagent-%KARAF_PROMETHEUS_VERSION%.jar=%KARAF_PROMETHEUS_PORT%:%KARAF_PROMETHEUS_CONFIG%
:SET_JAVA_EXTRA_OPTS_END

call "%DIRNAME%\karaf.bat" %*
