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

call "%DIRNAME%\karaf.bat" %*
