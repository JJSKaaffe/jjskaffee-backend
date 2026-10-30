# Decisiones de arquitectura (ADR) y spikes

> Fuente: adr-0001.md … adr-0014.md y spike-01 … spike-06 del Project ISW2_Joel; ADR-0015 y ADR-0016 se escribieron en este repo; ADR-0017 se copió del repo de documentación (`jjskaffee-docs/adr/adr-0017.md`). Los textos completos están en `doc/adr/` y `doc/spikes/`.
> Aquí está cada decisión, lo descartado, la evidencia y **lo que implica para el código**. Los análisis de costos y FODA de cada ADR (útiles para la sustentación, no para programar) siguen en el Project.

| ADR | Decisión | Estado |
|---|---|---|
| 0001 | Autenticación delegada a **Firebase Authentication**; aislamiento de datos por cafetería es responsabilidad del producto | Aceptado, validado (SPIKE-01, SPIKE-03); el claim `id_cafeteria` lo reemplaza ADR-0016 |
| 0002 | Todo movimiento financiero se guarda en **una transacción** (todo o nada) | Aceptado |
| 0003 | Categorías como **entidad propia por cafetería** con tipo (ingreso/egreso/menú); se archivan, no se borran | Aceptado |
| 0004 | Agregados con **consultas SQL agregadas + índices**, no sumando en código | Aceptado, validado (SPIKE-06) |
| 0005 | Hosting con reinicio automático y monitoreo; notificaciones con **Firebase Cloud Messaging** | Aceptado, validado (SPIKE-04) |
| 0006 | **Monolito sin estado** (stateless), escalado horizontal; no microservicios | Aceptado; organización interna en ADR-0015 |
| 0007 | Usuario y fecha/hora de cada movimiento los pone **el backend**, nunca el frontend | Aceptado |
| 0008 | POS con accesos directos, **deshacer**, código de colores y asistente de IA (< 8 s) | Aceptado |
| 0009 | POS operable con **teclado**, mouse y táctil | Aceptado |
| 0010 | Compatibilidad con **Chrome, Firefox y Edge** (estándares web + pruebas manuales) | Aceptado |
| 0011 | IA, notificaciones y exportación con **proveedores/librerías externas**; **sin pasarela de pagos** | Aceptado |
| 0012 | **PostgreSQL** (por Row Level Security nativo) | Aceptado |
| 0013 | Nube: **Google Cloud Platform** (Cloud SQL para PostgreSQL) | Aceptado |
| 0014 | Secretos en **Google Cloud Secret Manager**, leídos por un componente **Secrets Provider** | Propuesto (pedido por el profesor) |
| 0015 | **Monolito modular con módulos hexagonales**, verificado con ArchUnit | Aceptado |
| 0016 | Cafetería elegida con el encabezado **`X-Cafeteria-Id`**; rol por **membresía** (una persona, varias cafeterías) | Aceptado; reemplaza el claim de ADR-0001 y actualiza RF7 |
| 0017 | **Google Cloud API Gateway** como única entrada pública; Cloud Run privado | Aceptado; archivo y código listos, pendiente de montar en la nube |

---

## ADR-0001 · Aislar datos y delegar autenticación
- **Decisión**: Firebase Authentication. El filtrado de datos por cafetería lo hace el producto. *(El custom claim `id_cafeteria` que decía esta decisión lo reemplazó ADR-0016.)*
- **Descartados**: desarrollo propio (riesgo de seguridad), Auth0 (caro desde volúmenes intermedios), Supabase Auth (menor trayectoria, lock-in con la BD).
- **Evidencia (SPIKE-01)**: registro 1.181 ms, custom claim 427 ms, token 1 ms. **(SPIKE-03)** aislamiento validado con usuario de app limitado y `FORCE ROW LEVEL SECURITY`.
- **En el código**: nunca implementar login/contraseñas propias; el backend verifica el token de Firebase (`VerificadorDeToken` → `FirebaseAuthAdapter`), que solo devuelve el `uid`. El `UsuarioAutenticado` (id, idCafeteria, rol) se arma con la membresía (ADR-0016). Revisar si se acerca al límite gratuito (50.000 MAU).

## ADR-0002 · Exactitud de la información financiera
- **Decisión**: cada registro (en especial ventas compuestas base + adiciones) dentro de una transacción de BD.
- **En el código**: todo caso de uso que escribe pasa por `UnidadDeTrabajo.ejecutar(idCafeteria, ...)`. Aplica a ingresos, egresos, inventario y nómina. Meta: 0 % de discrepancias entre reportes semanales sumados y el mensual.

## ADR-0003 · Catálogos personalizados
- **Decisión**: tabla de categorías por cafetería con tipo (ingreso / egreso / menú). Descartados: columnas fijas (rígido) y EAV (consultas complejas, poca integridad).
- **En el código**: una categoría con historial **se archiva** (inactiva), no se elimina. Soportar ≥ 50 categorías por cafetería. Al registrar un egreso la categoría debe ser de egreso y estar activa.

## ADR-0004 · Velocidad con cálculo en la base de datos
- **Decisión**: `SUM`/`GROUP BY` en SQL con índices en columnas consultadas (`id_cafeteria`, fecha, categoría). Agregados precalculados solo si el volumen lo exige.
- **Evidencia (SPIKE-06)**: 10.000 movimientos → 8 ms con o sin índice. 200.000 movimientos → dashboard 30 días 83 ms → 8 ms con índice (−90 %); reporte anual 128 vs 132 ms (sin diferencia, lee casi toda la tabla).
- **En el código**: no traer todos los movimientos a Java para sumarlos; paginar historiales. Metas: venta en 1–2 s, dashboard 2–3 s con > 500 registros.

## ADR-0005 · Disponibilidad y notificaciones
- **Decisión**: hosting con reinicio automático y monitoreo; notificaciones con Firebase Cloud Messaging (descartados: desarrollo propio, OneSignal).
- **Evidencia (SPIKE-04)**: envío servidor → FCM 778 ms; end-to-end 751 ms (meta 5.000–10.000 ms). Funciona en primer y segundo plano. Lecciones: el `firebase-messaging-sw.js` va en la raíz del sitio; `firebase-admin` v14 usa API modular.
- **En el código**: el cálculo de alertas se dispara justo después de guardar el movimiento, de forma asíncrona, sin bloquear la venta.

## ADR-0006 · Arquitectura simple
- **Decisión**: monolito **sin estado** que escala horizontalmente. Microservicios descartados por complejidad operativa.
- **En el código**: no guardar sesión en memoria del servidor; usar pool de conexiones. Vigilar la BD compartida como siguiente cuello de botella. Organización interna: ADR-0015.

## ADR-0007 · Trazabilidad total
- **Decisión**: el backend captura automáticamente usuario y fecha/hora de cada movimiento (contra el "robo hormiga").
- **En el código**: `idCafeteria`, `registradoPor`/`idCajero` y `fechaHora` **nunca** llegan en el request; salen de `UsuarioAutenticado` y de un `Clock` inyectado. 100 % de movimientos con usuario, fecha y hora.

## ADR-0008 · Interfaz fácil y asistente de IA
- **Decisión**: POS con accesos directos visuales, mecanismo de **deshacer**, código de colores verde/rojo y asistente conversacional.
- **Metas**: venta < 5 s y máx. 3 clics; corrección en < 2 pasos; respuesta de IA < 8 s; balance neto entendible en < 5 s.
- **En el código**: deshacer venta = solo quien la registró y dentro de **60 s** (`Venta.TIEMPO_PARA_DESHACER`); pasado ese tiempo la anula el administrador. Costo estimado de API de IA: 200.000–600.000 COP/mes.

## ADR-0009 · Múltiples formas de interacción
- **Decisión**: todos los elementos de la pantalla de cobro operables por teclado (Tab, Enter confirma, Esc cancela) y también mouse/táctil.

## ADR-0010 · Navegadores
- **Decisión**: CSS/JS estándar; prueba manual en Chrome, Firefox y Edge antes de cada entrega. Resolución mínima 1366×768.

## ADR-0011 · Capacidades no diferenciales con proveedores externos
- **Decisión**: IA con un modelo de nivel medio (Claude, OpenAI o Gemini — **a confirmar**; SPIKE-02 quedó pendiente por créditos/API keys, sugerido Gemini por su nivel gratuito); notificaciones con FCM; exportación CSV/Excel/PDF con librerías existentes. **Sin pasarela de pagos** (SPIKE-05 descartado): registro financiero manual y cobro de la suscripción por fuera.
- **En el código**: si la IA falla, las alertas por reglas siguen funcionando. Enviar a la IA solo el contexto del período consultado; usar streaming si se puede.

## ADR-0012 · Motor de base de datos
- **Decisión**: PostgreSQL por su **Row Level Security** nativo (MySQL descartado: sin RLS maduro). Supabase queda como alternativa futura.
- **Práctica obligatoria (SPIKE-03)**: la app se conecta con un **usuario limitado** (p. ej. `app_jjskaffee`), nunca con el superusuario, y toda tabla multi-tenant usa `ALTER TABLE ... FORCE ROW LEVEL SECURITY`. La cafetería activa se fija por transacción (p. ej. `set_config('app.current_cafeteria', ..., true)`), y en el primer intento sin `FORCE` el dueño de la tabla veía todas las filas.

## ADR-0013 · Proveedor de nube
- **Decisión**: Google Cloud Platform, con **Cloud SQL para PostgreSQL**, por la sinergia con Firebase Auth y FCM (facturación y consola unificadas). Descartados: AWS, Azure (curva/sin sinergia), Railway/Render (escalan menos).

## ADR-0014 · Vault de secretos
- **Decisión**: Google Cloud Secret Manager para credenciales de PostgreSQL, API key de IA, FCM y API de anuncios. El backend las lee por un único componente **Secrets Provider** con una cuenta de servicio de solo lectura. Descartados: variables de entorno (sin auditoría ni rotación), HashiCorp Vault (operación costosa), AWS Secrets Manager (segunda nube).
- **En el código**: **ningún secreto en el repositorio**; `.env` en `.gitignore`, solo se versiona `.env.example` sin valores. En C4: contenedor "Vault de secretos" (nivel 2) y componente "Secrets Provider" (nivel 3).

## ADR-0015 · Monolito modular con módulos hexagonales
- **Decisión**: una sola aplicación Spring Boot dividida en módulos de negocio (`compartido`, `acceso`, `ingresos`, `egresos`, `movimientos`); cada módulo con `dominio`, `servicios` e `infraestructura`. Complementa ADR-0006.
- **Descartados**: hexagonal única por capas (no separa áreas de negocio), modular sin hexagonal (pierde dominio puro y pruebas sin BD), microservicios (ADR-0006).
- **En el código**: `ArquitecturaTest` verifica las capas dentro de cada módulo, que un módulo solo use de otro su `servicios.input..`, que `compartido` no dependa de ningún módulo y que no haya ciclos entre módulos ni entre paquetes. Si una regla falla, se corrige el cambio, no la regla.

## ADR-0016 · Cafetería por encabezado y rol por membresía
- **Decisión**: el token solo identifica a la persona (`uid`). Cada petición lleva `X-Cafeteria-Id`; el backend lee la membresía (tabla `usuarios`, una fila por persona y cafetería) con RLS de esa cafetería y toma el rol de ahí.
- **Descartados**: claim `id_cafeteria` (una cafetería por token, cambios esperan a que el token venza), claim con lista de cafeterías (mismos retrasos, límite de 1.000 bytes), sesión en el servidor (rompe ADR-0006).
- **En el código**: 401 sin token válido, 400 sin `X-Cafeteria-Id` o si no es UUID, 403 sin membresía activa. `GET /acceso/cafeterias` usa `app.current_uid` (`UnidadDeTrabajo.ejecutarConIdentidad`); nunca quedan fijadas a la vez `app.current_cafeteria` y `app.current_uid`. Permisos por rol en `Rol.permisos()`. Migración `db/07_multicafeteria.sql`.

## ADR-0017 · API Gateway delante de Cloud Run
- **Decisión**: Google Cloud API Gateway como única URL pública. Valida el token de Firebase (emisor `https://securetoken.google.com/jjskafee`, audiencia `jjskafee`) y es el único que puede invocar el backend (Cloud Run privado, `roles/run.invoker` solo para su cuenta de servicio). Complementa ADR-0001, ADR-0013 y ADR-0014.
- **Descartados**: Spring Cloud Gateway (otro servicio para programar y operar), Cloud Run directo (backend expuesto), reescrituras de Firebase Hosting (no validan tokens ni dejan Cloud Run privado).
- **En el código**: `infra/api-gateway/openapi.yaml` (OpenAPI 2.0) con una ruta por endpoint bajo `/api`, agrupadas con `tags` por módulo, `x-google-backend` con `APPEND_PATH_TO_ADDRESS` y `OPTIONS` sin seguridad para el preflight de CORS. `FirebaseAuthFilter` toma el token de `X-Forwarded-Authorization` si viene (el gateway pone el suyo en `Authorization`) y si no de `Authorization`; el backend sigue verificando token y membresía. Perfil `gcp` con `server.servlet.context-path=/api`; en local sin prefijo. `ContratoApiGatewayTest` verifica que el yaml y los controllers coincidan. Despliegue: `docs/despliegue/api-gateway.md`.

---

## Spikes
| Spike | Tema | Resultado |
|---|---|---|
| SPIKE-01 | Proveedor de autenticación | ✅ Firebase Auth (el custom claim `id_cafeteria` ya no se usa: ADR-0016) |
| SPIKE-02 | Tiempo de respuesta de proveedores de IA | ⚠️ Código listo; sin medición real por cuentas sin crédito/API key inválida. Pendiente |
| SPIKE-03 | Aislamiento multi-tenant | ✅ RLS forzado + usuario de app limitado |
| SPIKE-04 | Notificaciones en tiempo real | ✅ FCM, 751 ms end-to-end |
| SPIKE-05 | Pasarela de pagos | ❌ Descartado, fuera de alcance |
| SPIKE-06 | Rendimiento con volumen | ✅ Consultas agregadas con índices |
