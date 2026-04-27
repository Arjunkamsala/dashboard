@echo off
echo ===================================================
echo   Block Node Dashboard API - Spring Boot
echo ===================================================
echo.
echo Building and running... Please wait.
echo.

if exist apache-maven-3.9.6\bin\mvn.cmd (
    apache-maven-3.9.6\bin\mvn.cmd clean spring-boot:run
) else (
    mvn clean spring-boot:run
    if %errorlevel% neq 0 (
        echo.
        echo [ERROR] 'mvn' command failed or is not recognized.
        echo Please make sure Maven is installed and added to your PATH, 
        echo or run the project directly from your IDE ^(IntelliJ IDEA, Eclipse, etc.^).
    )
)
pause
