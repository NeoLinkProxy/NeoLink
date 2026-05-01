@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem
@rem SPDX-License-Identifier: Apache-2.0
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  Windows 平台 Gradle 启动脚本
@rem
@rem ##########################################################################

@rem 使用 Windows NT shell 为变量设置局部作用域
if "%OS%"=="Windows_NT" setlocal

@rem 设置UTF-8编码防止中文乱码
chcp 65001 >nul 2>&1

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem 通常不会使用该变量
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem 解析 APP_HOME 中的 "." 和 ".."，让路径更短。
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem 在这里添加默认 JVM 选项。也可以使用 JAVA_OPTS 和 GRADLE_OPTS 向此脚本传递 JVM 选项。
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem 查找 java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem 设置命令行

set CLASSPATH=


@rem 执行 Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" -jar "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" %*

:end
@rem 结束 Windows NT shell 中变量的局部作用域
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem 如果需要脚本自身的返回码，而不是 cmd.exe /c 的返回码，请设置 GRADLE_EXIT_CONSOLE 变量。
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%GRADLE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
