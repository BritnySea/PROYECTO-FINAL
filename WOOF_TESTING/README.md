# WOOF — Suite de Pruebas Automatizadas

Este directorio contiene las pruebas automatizadas del sistema WOOF escritas con **pytest**.
Las pruebas corren solas: no hay que ejecutar nada a mano.

## Estructura

```
WOOF_TESTING/
├── conftest.py              → Configuración global: fixtures, tokens, imágenes
├── pytest.ini               → Configuración de pytest (rutas, opciones, timeout)
├── requirements_test.txt    → Dependencias Python para las pruebas
├── .env.test.example        → Plantilla de variables de entorno (copiar como .env.test)
├── ejecutar_pruebas.bat     → Script Windows: instala + corre + genera gráficos
├── generar_graficos.py      → Lee resultados de pytest → genera 5 PNG para el documento
│
├── APP/test_iteracion_3/    → Pruebas de la app móvil (via API backend)
│   ├── test_autenticacion.py
│   ├── test_reporte_perdido.py
│   ├── test_reporte_encontrado.py
│   ├── test_coincidencias.py
│   └── test_mis_reportes.py
│
├── WEB/test_iteracion_4/    → Pruebas del panel web (via Firestore REST API)
│   ├── test_autenticacion_admin.py
│   ├── test_gestion_usuarios.py
│   └── test_supervision_reportes.py
│
└── INTEGRACION/test_iteracion_5/   → Pruebas de integración del sistema completo
    ├── test_seguridad.py
    ├── test_rendimiento.py
    ├── test_regresion.py
    └── test_end_to_end.py
```

## Documentación de cada archivo de pruebas

- [APP — Autenticación](APP/test_iteracion_3/COMO_FUNCIONA.md)
- [APP — Reporte Perdido](APP/test_iteracion_3/COMO_FUNCIONA.md)
- [APP — Reporte Encontrado](APP/test_iteracion_3/COMO_FUNCIONA.md)
- [WEB — Panel Admin](WEB/test_iteracion_4/COMO_FUNCIONA.md)
- [Integración — Seguridad, Rendimiento, Regresión, E2E](INTEGRACION/test_iteracion_5/COMO_FUNCIONA.md)

## Inicio rápido

```bash
# 1. Instalar dependencias
pip install -r requirements_test.txt

# 2. Configurar credenciales
copy .env.test.example .env.test
# Editar .env.test con tu Firebase API Key y cuentas de prueba

# 3. Arrancar el backend WOOF
cd ../WOOF_BACKEND && uvicorn main:app --reload

# 4. Correr todas las pruebas
cd ../WOOF_TESTING && pytest

# 5. Generar gráficos PNG para el documento
python generar_graficos.py
```

O simplemente doble clic en `ejecutar_pruebas.bat`.
