@ECHO OFF
set JAVA_HOME=D:/usr/java/jdk1.8.0_131
set _RUNJAVA=%JAVA_HOME%/bin/java.exe
set "KOTLINCLASSPATH=classes;bin"
@REM for 过程中会自动添加 setlocal ,所以设置 变量时使用 call 传递
for %%F in (lib\*.jar) do (
	call :addcp %%F
)

goto extlibe

:addcp
set KOTLINCLASSPATH=%KOTLINCLASSPATH%;%1
goto :eof

:extlibe
echo KOTLINCLASSPATH=%KOTLINCLASSPATH%

@echo ------------------
%_RUNJAVA% -classpath "%KOTLINCLASSPATH%" org.shoukaiseki.impasset.ImpAssetGuiMain
pause


@ECHO OFF
goto end
:end
@ECHO ON
