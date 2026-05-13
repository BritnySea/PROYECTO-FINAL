@echo off
REM ══════════════════════════════════════════════════════
REM  WOOF — Ejecutor de pruebas automatizadas (Windows)
REM  Doble clic o ejecutar desde PowerShell/CMD
REM ══════════════════════════════════════════════════════

title WOOF - Suite de Pruebas

echo.
echo  ============================================
echo   WOOF - Pruebas Automatizadas del Sistema
echo  ============================================
echo.

cd /d "%~dp0"

REM Verificar que existe .env.test
if not exist ".env.test" (
    echo  [AVISO] No se encontro .env.test
    echo  Copia .env.test.example como .env.test y completa los valores
    echo  Las pruebas sin credenciales igual corren parcialmente.
    echo.
)

REM Crear carpeta de resultados
if not exist "resultados" mkdir resultados

REM Instalar dependencias
echo  [1/3] Verificando dependencias...
pip install -r requirements_test.txt -q

REM Ejecutar pytest
echo.
echo  [2/3] Ejecutando pruebas con pytest...
echo.
pytest --tb=short -v

REM Generar gráficos
echo.
echo  [3/3] Generando graficos para el documento...
python generar_graficos.py

echo.
echo  ============================================
echo   Listo. Graficos en: resultados\
echo  ============================================
echo.
pause
