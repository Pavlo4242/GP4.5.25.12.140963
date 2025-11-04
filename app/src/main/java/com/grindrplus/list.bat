@echo off
setlocal EnableDelayedExpansion

rem === OUTPUT FILE ===
set "OUTPUT=file_list.txt"
type nul > "%OUTPUT%"

rem === COUNTER ===
set "COUNT=0"

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
        dir /S /B "%%~D" > temp_list.txt
        type temp_list.txt >> "%OUTPUT%"
        for /F %%G in ('find /C /V "" ^< temp_list.txt') do set /A COUNT+=%%G
        del temp_list.txt
    ) else (
        echo [WARNING] Not found: %%~D
    )
)

rem === FINAL OUTPUT ===
echo.
echo ===========================================
echo Done! Found !COUNT! files.
echo List saved to: %OUTPUT%
echo ===========================================
echo.
echo Press any key to close...
pause >nul