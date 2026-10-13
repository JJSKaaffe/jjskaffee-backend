# Atributos de calidad, escenarios y tácticas

> Fuentes: "Tarea pre-drivers.docx", "Escenarios de calidad.docx" (la versión "(1)" es un subconjunto de la misma), tradeoff final version.xlsx, Dot voting.xlsx y Mapa de empatía (`mapa-empatia.png`).

## Prioridad (matriz de trade-off, 11 = máxima)
| Atributo | Administrador | Cajero | Total | Peso global |
|---|---|---|---|---|
| Seguridad | 11 | 8 | 19 | 14,4 % |
| Rendimiento | 8 | 11 | 19 | 14,4 % |
| Capacidad | 9 | 9 | 18 | 13,6 % |
| Usabilidad-UX | 7 | 10 | 17 | 12,9 % |
| Disponibilidad | 10 | 5 | 15 | 11,4 % |
| Confiabilidad | 5 | 7 | 12 | 9,1 % |
| Accesibilidad | 6 | 6 | 12 | 9,1 % |
| Escalabilidad | 4 | 3 | 7 | 5,3 % |
| Portabilidad | 3 | 4 | 7 | 5,3 % |
| Capacidad de ser auditado | 2 | 2 | 4 | 3,0 % |
| Interoperabilidad | 1 | 1 | 2 | 1,5 % |

Lectura rápida: el administrador prioriza **seguridad/aislamiento** y disponibilidad; el cajero prioriza **rendimiento** y usabilidad del punto de venta.

## Escenarios priorizados (con su medida)
**Disponibilidad**
- Latencia de alertas: evento que supera un umbral → alerta en la interfaz en 5–10 s.
- Fallback: si la IA falla, las alertas por reglas siguen funcionando sin detener la operación.
- Uptime ~99 % en la ventana operativa (6:00 a. m. – 9:00 p. m.).

**Capacidad**
- Hasta 50 categorías personalizadas por cafetería sin romper la maquetación.
- Límite de tamaño/formato del logo de la cafetería.
- Más de 10.000 movimientos financieros anuales por cafetería sin degradar.

**Rendimiento**
- Dashboard on-demand < 3 s con más de 500 movimientos.
- Confirmación de venta en 1–2 s.
- Buscador de productos del POS < 300 ms por tecla (filtrar en el navegador).
- Anuncios del plan gratis no suman más de 500 ms al POS.

**Seguridad**
- Contraseñas con bcrypt/Argon2, nunca en texto plano (delegado a Firebase, ADR-0001).
- Cajero que intenta rutas de administración: bloqueado (100 %) y devuelto a su pantalla de ventas; el bloqueo se hace **en el backend**, no solo ocultando botones.
- Toda consulta filtra por `id_cafeteria`; acceso por URL a otra cafetería → 403/404; 0 % de fugas.
- Nadie altera su saldo de créditos manipulando peticiones.

**Escalabilidad**: 10 → 50 cafeterías concurrentes en la misma BD sin degradación; ventas simultáneas en hora pico (7–9 a. m.) sin bloqueos cruzados.

**Usabilidad**: cajero nuevo registra su primera venta en < 5 min y 3–4 clics; administrador configura categorías/umbrales sin manual; IA explica motivo + acción en < 8 s; créditos agotados bloquean solo el chatbot, no el POS; anuncios en banners periféricos fijos.

**Confiabilidad**: Σ reportes semanales = reporte mensual (0 % discrepancia); utilidad neta exacta sin errores de redondeo; IA compara días equivalentes (viernes vs. viernes) sin falsos positivos por festivos.

**Capacidad de ser auditado**: 100 % de ventas/egresos con usuario, fecha y hora; anulación = registro inmutable con motivo y usuario autorizador; **sin borrado físico** de registros contables (borrado lógico).

**Accesibilidad**: botones principales con texto y color, no solo iconos; flujo de cobro completo solo con teclado; tooltips en funciones complejas.

**Portabilidad**: Chrome, Edge y Firefox de escritorio; resoluciones ≥ 1366×768.

**Interoperabilidad**: exportar reportes y movimientos a CSV/PDF/Excel; API JSON estructurada.

Los escenarios con fuente/estímulo/artefacto/entorno/respuesta/medida están en `priorizacion-y-escenarios-qa.md` (hoja "QApulidos" = versión refinada). Los tiempos máximos por transacción están en `matriz-de-tiempos.md` (registrar ingreso o egreso ≤ 3 s; consultar movimientos con filtros ≤ 4 s; historial ≤ 5 s).

## Tácticas acordadas para cumplir los escenarios
- **Alertas 5–10 s**: motor de reglas asíncrono disparado justo después del insert (no tarea programada cada X minutos), sin bloquear el registro de la venta.
- **10.000 movimientos/año**: índices en `fecha`, `id_cafeteria`, `id_categoria`; paginar historiales (≤ 50 registros por página).
- **50 categorías**: listas con scroll interno o paginación; lazy loading.
- **Venta en 1–2 s**: no recalcular el catálogo en cada venta; petición asíncrona.
- **Dashboard 2–3 s**: `SUM`/`GROUP BY` en BD; cache temporal mientras no cambie el filtro.
- **Aislamiento**: filtro `id_cafeteria` obligatorio inyectado desde el usuario autenticado + RLS en PostgreSQL.
- **Rutas no autorizadas**: verificación de rol en el backend antes de ejecutar cualquier acción.
- **Escalabilidad**: backend stateless + pool de conexiones.
- **Confiabilidad**: reportes siempre desde la misma fuente de verdad (tabla de movimientos), no desde agregados cacheados; transacciones.
- **Auditoría**: `registrado_por_id` y `fecha_hora` poblados por el backend; considerar tabla de log de cambios para ediciones.
- **Teclado**: Tab en todo, Enter confirma, Esc cancela; HTML semántico y ARIA.
- **Exportación**: librerías probadas (p. ej. SheetJS para Excel/CSV; una librería de PDF del stack).
- **Deshacer**: notificación temporal "Venta registrada — Deshacer".
- **IA < 8 s**: streaming y contexto limitado al período consultado.
- **Balance en < 5 s**: balance neto como el elemento más grande, colores verde/rojo.

## Catálogo de atributos (pre-drivers)
- **Explícitos**: personalización; alertas en (semi) tiempo real; seguridad de credenciales; control de acceso por roles; aislamiento multi-tenant; usabilidad para no técnicos; dashboard al consultar; explicabilidad de la IA; compatibilidad de navegadores; múltiples cafeterías en una BD; monetización por niveles.
- **Implícitos**: exactitud de cálculos; consistencia de datos; mantenibilidad; rendimiento; portabilidad; trazabilidad; auditabilidad; recuperabilidad ante fallos; interoperabilidad futura; extensibilidad; consistencia visual; accesibilidad; privacidad entre cafeterías; mensajes de error claros.
- **Ambos (alcance por precisar)**: escalabilidad; uptime; calidad de recomendaciones de IA; latencia de alertas; anuncios en plan gratis; seguridad de créditos; límites de personalización; automatización del inventario; coherencia documentación-implementación; mecanismo técnico de aislamiento; rendimiento con crecimiento; frecuencia de refresco del dashboard; tipos de alertas configurables.

## Escenarios relevantes para ingresos y egresos (del catálogo EXP/IMP/AMB)
- EXP01-03: eliminar una categoría no borra ni corrompe movimientos históricos.
- EXP04-01: un cajero que intenta editar un egreso de administración es bloqueado.
- EXP05-01 / AMB35-01: filtro de tenant obligatorio; acceso a otra cafetería → 403/404.
- EXP03-05: el hash de contraseña nunca aparece en respuestas JSON.
- IMP12-02: ventas con adiciones sin diferencias de redondeo.
- IMP12-05: ventas anuladas restan correctamente del saldo del día.
- IMP13-02: editar un ingreso actualiza los totales diarios, semanales y mensuales.
- IMP13-03 / IMP18-03: cambiar o desactivar un producto no altera nombre ni precio en ventas pasadas (el detalle guarda el precio unitario de ese momento).
- IMP17-02: al editar un movimiento se guarda quién y cuándo lo editó.
- IMP18-02 / IMP18-04: anulación con motivo y usuario; borrado lógico.
- IMP19-01 / IMP19-04: sin registros parciales ni duplicados; rollback completo.
- IMP19-05: logs de error detallados en el servidor.
- IMP25-01 / IMP25-03: errores de validación que señalan el campo exacto.
- AMB34-01 / AMB34-02: los endpoints y modelos deben coincidir con lo documentado; documentar la API con Swagger/OpenAPI.
