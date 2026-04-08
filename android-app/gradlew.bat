@ECHO OFF
SET DIRNAME=%~dp0
SET CLASSPATH=%DIRNAME%\gradle\wrapper\gradle-wrapper.jar
IF DEFINED JAVA_HOME (
  SET JAVA_EXE=%JAVA_HOME%\bin\java.exe
) ELSE (
  SET JAVA_EXE=java.exe
)
"%JAVA_EXE%" -Xmx64m -Xms64m -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
