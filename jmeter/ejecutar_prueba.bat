@echo off
REM ---------------------------------------------------------------------------
REM Ejecuta el plan de rendimiento en modo no-GUI, genera el reporte HTML y el
REM resumen contra los requisitos.
REM
REM Uso:      ejecutar_prueba.bat <nombre_corrida> [opciones -J de JMeter]
REM Ejemplo:  ejecutar_prueba.bat linea_base
REM           ejecutar_prueba.bat linea_base_10min "-Jduracion=600"
REM  (en Windows las opciones -J van entre comillas porque cmd separa en el signo =)
REM
REM Si jmeter no esta en el PATH:  set JMETER_HOME=C:\apache-jmeter-5.6.3
REM ---------------------------------------------------------------------------
setlocal
if "%~1"=="" (
  echo Indique un nombre para la corrida, p. ej.: ejecutar_prueba.bat linea_base
  exit /b 1
)
set NOMBRE=%~1
shift
set EXTRA=
:args
if "%~1"=="" goto run
set EXTRA=%EXTRA% %1
shift
goto args

:run
set DIR=%~dp0
set SALIDA=%DIR%resultados\%NOMBRE%
if defined JMETER_HOME (set JMETER="%JMETER_HOME%\bin\jmeter.bat") else (set JMETER=jmeter.bat)

if exist "%SALIDA%" rmdir /s /q "%SALIDA%"
mkdir "%SALIDA%"

echo ^>^> Corrida %NOMBRE% - resultados en %SALIDA%
call %JMETER% -n -t "%DIR%ElectroShop_Rendimiento.jmx" -l "%SALIDA%\resultados.jtl" -j "%SALIDA%\jmeter.log" -e -o "%SALIDA%\reporte" -Jjmeter.save.saveservice.output_format=csv -Jjmeter.save.saveservice.print_field_names=true %EXTRA%

python "%DIR%analizar_resultados.py" "%SALIDA%\resultados.jtl" > "%SALIDA%\resumen.txt"
type "%SALIDA%\resumen.txt"
echo.
echo ^>^> Reporte HTML: %SALIDA%\reporte\index.html
endlocal
