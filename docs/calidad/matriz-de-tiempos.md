# Matriz de tiempos del sistema

> Fuente: Matriz de Tiempos del Sistema.xlsx. Tiempos máximos por transacción (datos mínimos vs. datos máximos).


## Hoja: Hoja1

| Datos minimos |  |  |
|---|---|---|
| Transaccion | Tiempo Maximo | Criterio de rendimiento esperado |
| Inicio de sesión | ≤5 segundos por operación | El sistema debe validar las credenciales y permitir el acceso del usuario sin retrasos perceptibles. |
| Registrar administrador | ≤5 segundos por operación | El sistema debe validar los datos ingresados, crear la cuenta y confirmar el registro sin retrasos perceptibles. |
| Registrar cafetería | ≤3 segundos por operación | La información de la cafetería debe almacenarse y quedar disponible inmediatamente después del registro. |
| Registrar cajero | ≤4 segundos por operación | El sistema debe validar los datos del cajero y confirmar su registro sin retrasos perceptibles. |
| Consultar productos del menú | ≤2 segundos por consulta | Los productos disponibles y sus precios deben mostrarse rápidamente para facilitar el registro de ventas. |
| Registrar una venta | ≤3 segundos por operación | El sistema debe calcular el total, registrar el detalle de la venta y confirmar la operación sin retrasos perceptibles. |
| Registrar un ingreso | ≤3 segundos por operación | El sistema debe almacenar el ingreso con su información correspondiente y confirmar el registro rápidamente. |
| Registrar un egreso | ≤3 segundos por operación | El sistema debe almacenar el egreso y actualizar la información financiera sin retrasos perceptibles. |
| Consultar inventario | ≤2 segundos por consulta | El inventario actual de la cafetería debe mostrarse rápidamente con las cantidades disponibles. |
| Registrar entrada de inventario | ≤3 segundos por operación | El sistema debe registrar la entrada y actualizar automáticamente el stock sin retrasos perceptibles. |
| Registrar salida de inventario | ≤3 segundos por operación | El sistema debe validar la disponibilidad, registrar la salida y actualizar el stock rápidamente. |
| Consultar alertas | ≤2 segundos por consulta | Las alertas financieras y de inventario disponibles deben mostrarse rápidamente al administrador. |
| Datos maximos |  |  |
| Transaccion | Tiempo Maximo | Criterio de rendimiento esperado |
| Consultar historial de movimientos financieros | ≤5 segundos por consulta | El historial debe cargarse correctamente aun cuando existan numerosos registros de ingresos y egresos. |
| Consultar historial de inventario | ≤5 segundos por consulta | El sistema debe mostrar los movimientos de inventario sin retrasos significativos aun cuando exista un volumen elevado de registros. |
| Generar dashboard financiero | ≤5 segundos por operación | El dashboard debe calcular y mostrar las métricas correspondientes al período seleccionado sin errores ni retrasos significativos. |
| Generar reporte financiero semanal | ≤6 segundos por operación | El reporte debe procesar los movimientos del período semanal y estar disponible para su consulta sin errores. |
| Generar reporte financiero mensual | ≤6 segundos por operación | El sistema debe consolidar los ingresos, egresos y utilidad del mes y presentar el reporte correctamente. |
| Generar reporte financiero anual | ≤8 segundos por operación | El sistema debe procesar la información acumulada del año y generar el reporte completo sin errores. |
| Comparar períodos financieros | ≤5 segundos por operación | El sistema debe comparar automáticamente el período seleccionado con el período anterior y mostrar la variación obtenida. |
| Consultar productos más vendidos | ≤5 segundos por consulta | El sistema debe procesar las ventas del período seleccionado y mostrar los productos con mayor cantidad de ventas. |
| Consultar movimientos con filtros | ≤4 segundos por consulta | Los resultados deben mostrarse rápidamente al aplicar filtros por fecha, categoría o tipo de movimiento. |
| Generar alertas automáticas | ≤5 segundos por operación | El sistema debe analizar las variaciones configuradas y generar las alertas correspondientes sin retrasos significativos. |
