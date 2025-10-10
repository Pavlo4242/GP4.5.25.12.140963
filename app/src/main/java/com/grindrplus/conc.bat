@echo off
set "source_dir=D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\140963\app\src\main\java\com\grindrplus"  REM Replace with your directory path
set "output_file=entrire.txt"  REM Replace with desired output name

if not exist "%source_dir%" (
    echo Source directory does not exist!
    exit /b 1
)

echo. > "%output_file%"
for /r "%source_dir%" %%f in (*.kt) do (
    echo --- File: %%f --- >> "%output_file%"
    type "%%f" >> "%output_file%"
    echo. >> "%output_file%"
)

echo Done!
pause