@echo off
set "source_file=file_list.txt"
set "output_file=2201.txt" REM Replace with desired output name
if not exist "%source_file%" (
    echo Source file %source_file% does not exist!
    exit /b 1
)
echo. > "%output_file%" 
for /f "delims=" %%f in ('type "%source_file%"') do (
    echo --- File: %%f --- >> "%output_file%"
    type "%%f" >> "%output_file%"
    echo. >> "%output_file%"
)
echo Done!
pause