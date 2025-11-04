@echo off
setlocal EnableDelayedExpansion
rem === OUTPUT FILE ===
set "OUTPUT=401.txt"
type nul > "%OUTPUT%"
rem === COUNTER ===
set "COUNT=0"
rem === TEMP LIST FOR PATHS ===
set "TEMP_LIST=temp_list.txt"
type nul > "%TEMP_LIST%"
rem === PROCESS EACH DIRECTORY - FIXED: Use quoted paths in single for loop without multi-line parsing ===
for %%D in (
    "D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\utils"
    "D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\ui"
    "D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\tasks"
    "D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\model"
    "D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\mappers"
    "D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\dao"
    "D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\converters"
    "D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence"
    "D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks"
    "D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\core\http"
    "D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\core"
    "D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\commands"
    "D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\bridge"
) do (
    if exist "%%~D\" (
        echo Scanning: %%~D
        dir /S /B "%%~D\*.kt" >> "%TEMP_LIST%"
    ) else (
        echo [WARNING] Not found: %%~D
    )
)
rem === CONCATENATE FROM TEMP LIST ===
for /f "delims=" %%F in ('type "%TEMP_LIST%"') do (
    set /A COUNT+=1
    echo Found: %%F
    echo --- File: %%F --- >> "%OUTPUT%"
    type "%%F" >> "%OUTPUT%"
    echo. >> "%OUTPUT%"
)
del "%TEMP_LIST%" 2>nul
rem === FINAL OUTPUT ===
echo.
echo ===========================================
echo Done! Found !COUNT! Kotlin files concatenated to %OUTPUT%
echo ===========================================
echo.
echo Press any key to close...
pause >nul