# JJSKaffee · Backend (monolito modular)

Sistema web SaaS multi-tenant para que cafeterías pequeñas e independientes de Colombia controlen sus ingresos y egresos, con reportes, comparación entre períodos y alertas configurables. Proyecto de Ingeniería de Software II (UCO). Este repositorio es el **backend**; Joel es responsable de la funcionalidad **registrar ingresos** (el módulo de egresos existe, pero su lógica no se toca). El frontend (Vue 3) vive en otro repo de la organización GitHub `JJSKaaffe`.

Responde y escribe comentarios, mensajes de commit y nombres de dominio **en español**, como el código existente.

## Stack
- Java 27, Spring Boot 4.1.1 (`spring-boot-starter-webmvc`, `spring-boot-starter-validation`, `spring-boot-starter-jdbc`), Maven wrapper. IDE: IntelliJ. No agregar dependencias sin preguntar.
- Persistencia: **JDBC con `JdbcTemplate`, sin JPA** (ArchUnit lo prohíbe). PostgreSQL en Cloud SQL (GCP) con **Row Level Security**.
- Autenticación: **Firebase Authentication**. El token solo identifica a la persona (`uid`); **no** lleva la cafetería. Nunca implementar login ni guardar contraseñas propias.
- Selección de cafetería: encabezado **`X-Cafeteria-Id`** en cada petición; el backend comprueba la membresía en la tabla `usuarios` y toma el rol de esa cafetería (ADR-0016).
- Secretos: **Google Cloud Secret Manager** vía un componente `SecretsProvider`. Ningún secreto en el repo; solo `.env.example` sin valores.
- Notificaciones: Firebase Cloud Messaging. Entrada pública por un **API Gateway** (`/api/...`).
- CI en GitHub: **Trivy** y **ArchUnit**. Si Trivy reporta CVEs, se fijan versiones en `<properties>` del `pom.xml` (ya hay overrides de Tomcat y Jackson).

## Comandos
- `./mvnw test` (en Windows `.\mvnw.cmd test`): pruebas unitarias + `ArquitecturaTest`, sin base de datos. Correrlas siempre después de cambiar código.
- `./mvnw verify`: además corre las pruebas `*IT` contra PostgreSQL (necesitan la base creada con todos los scripts de `db/` y `.env` o las variables `JJSKAFFEE_DB_*`).
- `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`: levantar la API en local (lee `.env`; ver `.env.example`).
- `trivy fs --scanners vuln,secret,misconfig --severity HIGH,CRITICAL --ignore-unfixed .`: el mismo escaneo del CI (Trivy 0.75.0).

## Base de datos
Scripts en `db/` (se corren a mano en pgAdmin y en CI, en orden). No hay herramienta de migraciones: un cambio de esquema es un script nuevo numerado, y los seeds de prueba van siempre en scripts aparte de las migraciones.
- `01_crear_usuario_y_base.sql`: rol `app_jjskaffee` (sin superusuario ni BYPASSRLS) y base `jjskaffee`.
- `02_esquema.sql`: tablas, RLS forzado, vista `v_movimiento` con `security_invoker`, permisos mínimos.
- `03_seed_cafeterias_y_catalogo.sql` y `04_seed_usuarios.sql`: cafeterías A y B con los UUID de `DatosPrueba`.
- `05_cafeteria_activa_e_idempotencia.sql`: `cafeteria.activo`, `venta.clave_idempotencia` + `hash_solicitud` con UNIQUE por cafetería.
- `06_seed_cafeteria_inactiva.sql`: cafetería C inactiva.
- `07_multicafeteria.sql`: membresías con UNIQUE `(id_cafeteria, firebase_uid)`, `app.current_uid` + `app_uid_actual()`, políticas de solo lectura por uid en `usuarios` y `cafeteria`, `SELECT (nombre)` sobre `cafeteria`.
- `08_seed_multicafeteria.sql`: Laura también es CAJERO en la cafetería B.
- `09_ingresos_libres.sql`: tabla `ingreso_libre` (RLS forzado, FK compuestas, idempotencia, borrado lógico) y `v_movimiento` con los ingresos libres como INGRESO.
- `10_seed_categorias_ingreso.sql`: dos categorías de INGRESO activas y una archivada en la cafetería A.
- Un script nuevo de BD se agrega a esta lista y a `.github/workflows/seguridad.yml` en la misma fase en que se crea.

Los nombres de tabla siguen el MER del equipo. La tabla `usuarios` guarda **membresías**: una fila por persona y cafetería, con su rol (UNIQUE `(id_cafeteria, firebase_uid)`). Las tablas de catálogo, `usuarios` y `cafeteria` son de solo lectura para la app; no hay `DELETE` en ninguna tabla.

## Arquitectura: monolito modular con módulos hexagonales (obligatoria)
Una sola aplicación Spring Boot (un jar, un servicio en Cloud Run), dividida en **módulos de negocio**; cada módulo se organiza por dentro con **arquitectura hexagonal** (ADR-0015).

Paquete raíz `co.edu.uco.jjskafee` (con una sola "f", no corregirlo). Módulos:
- `compartido`: kernel compartido. `UsuarioAutenticado`, `Rol`, excepciones, `Producto`, `Categoria`, `TipoProducto`, `TipoCategoria`, `UnidadDeTrabajo`, `CatalogoRepository` + `PgCatalogoRepository`, `PgUnidadDeTrabajo`, `ConversionesSql`, `ManejadorDeErrores`, `CorsConfig`, `Clock` y la constante del atributo de petición con el usuario autenticado.
- `acceso`: identidad, cafeterías del usuario y rol. `VerificadorDeToken`, `FirebaseAuthFilter`, `FirebaseAuthAdapter`, `FirebaseConfig`, repositorio de membresías; `GET /acceso/cafeterias` y `GET /acceso/yo`.
- `ingresos`: ventas (`Venta`, `DetalleVenta`, `EstadoVenta`) e ingresos libres (`IngresoLibre`); `RegistrarIngreso`, `RegistrarIngresoLibre`, `IngresosController`, `PgVentaRepository`, `PgCafeteriaRepository`.
- `egresos`: `Egreso`, `RegistrarEgreso`, `EgresosController`, `PgEgresoRepository`. **Solo se mueve; la lógica no se cambia.**
- `movimientos`: `Movimiento`, `FiltroMovimientos`, `ResultadoMovimientos`, `TipoMovimiento`, `ConsultarMovimientos`, `MovimientosController`, `PgMovimientoRepository` (lee la vista `v_movimiento`).

Dentro de **cada** módulo:
- `dominio`: entidades en Java puro, enums y excepciones. Sin Spring, sin JPA, sin servlets.
- `servicios.casouso`: casos de uso en Java puro (**sin `@Service` ni nada de Spring, `java.sql` o Firebase**); se instancian en la clase de configuración del módulo.
- `servicios.input.ports` / `servicios.output.ports`: solo **interfaces**.
- `servicios.input.dto.request|response`: records; los request llevan validaciones `jakarta.validation`.
- `infraestructura.input`: controllers y filtros; solo usan **puertos de entrada** (nunca `casouso` ni `infraestructura.output`).
- `infraestructura.output`: repositorios JDBC y adaptadores de servicios externos.
- `infraestructura.configuracion`: crea los casos de uso y adaptadores del módulo.

Entre módulos:
- Un módulo solo puede usar de otro lo que está en `<modulo>.servicios.input..` (puertos de entrada y DTO). Nunca su dominio, casos de uso ni infraestructura.
- `compartido` no depende de ningún módulo.
- Sin ciclos entre módulos ni entre paquetes.

`ArquitecturaTest` verifica todo lo anterior. Si un cambio rompe `ArquitecturaTest`, el cambio está mal, no la prueba; nunca se debilita una regla para que pase.

**Estado**: en la rama `feature/monolito-modular-ingresos` el código ya está organizado en los módulos de arriba (ADR-0015), con multi-cafetería por encabezado y membresías (ADR-0016), ingresos libres, catálogo de venta y el flujo completo de registrar ingreso probado por HTTP (`FlujoRegistrarIngresoIT`). `main` todavía tiene la versión por capas hasta que Joel haga el merge. Colección de Postman del flujo en `docs/postman/`. Pendiente: perfil `gcp` (Cloud Run + Cloud SQL) y `SecretsProvider` con Secret Manager (ADR-0014); hoy los secretos vienen de variables de entorno. Anular ingresos libres (hay columnas `anulado`/`motivo_anulacion`, sin caso de uso ni `GRANT UPDATE` todavía).

Detalle de capas, adaptadores pendientes y recorrido de una petición:
@docs/arquitectura/backend-hexagonal.md

## Funcionalidad principal: registrar ingreso
Flujo que debe funcionar de punta a punta:
1. **Login**: el frontend obtiene el token de Firebase.
2. **Elegir cafetería**: `GET /acceso/cafeterias` (solo token) devuelve las cafeterías del usuario con su rol en cada una.
3. **Rol**: con `X-Cafeteria-Id`, el backend arma `UsuarioAutenticado` con el rol de esa cafetería; `GET /acceso/yo` devuelve rol y permisos para que el frontend muestre el menú correcto.
4. **Registrar el ingreso**: venta (`POST /ingresos`) o ingreso libre (`POST /ingresos/libres`, solo administrador).
5. **Verlo reflejado**: `GET /movimientos` lo muestra con los totales del filtro.

Respuestas de acceso: sin token o token inválido → 401; falta `X-Cafeteria-Id` o no es UUID → 400; sin membresía activa en esa cafetería → 403.

## Reglas no negociables
- **Multi-tenant**: toda operación de negocio corre dentro de `UnidadDeTrabajo.ejecutar(usuario.idCafeteria(), ...)`. En BD: usuario de app limitado (nunca superusuario), `FORCE ROW LEVEL SECURITY` en toda tabla con datos de cafetería y la cafetería fijada por transacción. 0 % de fugas entre cafeterías.
- **Multi-cafetería por persona**: una persona puede pertenecer a varias cafeterías con un rol distinto en cada una. La única consulta sin cafetería fijada es la de "mis cafeterías", que usa `app.current_uid`; nunca se fijan `app.current_cafeteria` y `app.current_uid` en la misma transacción.
- **Transacción todo o nada** para cada movimiento (venta con sus detalles juntos).
- **Trazabilidad**: `idCafeteria`, usuario que registra y `fechaHora` los pone el servidor (desde `UsuarioAutenticado` y un `Clock` inyectado). Nunca aceptarlos en el cuerpo del request.
- **Ingresos**: una venta la registran cajero o administrador; el total se calcula en el servidor con los precios del catálogo y el detalle guarda el precio unitario del momento. Un **ingreso libre** (monto + categoría de tipo INGRESO activa) solo lo registra el ADMINISTRADOR. Monto > 0, máximo 2 decimales y 10 dígitos enteros, sin redondear. La cafetería debe estar activa. Deshacer venta: solo quien la registró y dentro de 60 s.
- **Consulta**: el cajero solo consulta lo que él registró; el administrador ve todo lo de la cafetería.
- Egresos: solo ADMINISTRADOR, monto > 0, categoría de egreso activa.
- Categorías con historial se **archivan**, no se borran. Registros contables con **borrado lógico** y anulación con motivo.
- Agregados y totales con SQL (`SUM`/`GROUP BY`) e índices (`id_cafeteria`, fecha, categoría); nunca cargar todo a memoria para sumar. Paginar historiales.
- Dinero en `BigDecimal`, IDs `UUID`, tiempo `Instant` en UTC. Entidades con fábrica que valida (`crear`/`registrar`) y `reconstruir` para leer de BD.
- Metas de tiempo: registrar ingreso o egreso ≤ 3 s (venta ideal 1–2 s); consultar movimientos con filtros ≤ 4 s.

## Decisiones vigentes cuando los documentos se contradicen
Hay textos antiguos que dicen "una sola cafetería", "una cafetería por usuario", "id_cafeteria como custom claim", "todo ingreso es una venta", "sin IA", "sin nómina", "MVC", "Node.js" o "dashboard en tiempo real". Lo vigente es:
- **Monolito modular** con **módulos hexagonales** (ADR-0015), desplegado como una sola aplicación sin estado.
- **Multi-tenant** y **multi-cafetería por persona**, con la cafetería elegida por encabezado (ADR-0016; reemplaza el claim del ADR-0001 y actualiza RF7).
- **Ingreso = venta o ingreso libre** (el libre, solo administrador).
- **Sí IA** (explica alertas que se calculan por reglas), **nómina básica**, **inventario incluido**, **dashboard calculado al consultar**, **sin pasarela de pagos** (registro manual).

Ver `docs/negocio/negocio-y-alcance.md`. Si encuentras otra contradicción que no esté en esta lista, avisa en vez de elegir por tu cuenta.

## Cómo trabajar en este repo
- Antes de implementar algo, revisa el RF y los ADR relacionados.
- Cada caso de uso nuevo lleva prueba con los fakes de `RepositoriosEnMemoria`; cada regla de dominio, prueba en `dominio/*Test`; cada endpoint o regla de RLS nueva, prueba `*IT`.
- Trabaja en ramas; no hagas push ni merge a `main` sin que Joel lo pida.
- El diagrama hexagonal muestra "Comandos" entre DTO y caso de uso, pero el código no los usa: pregunta antes de introducirlos.

## Documentación (léela cuando la tarea lo requiera)
Índice completo en `docs/README.md`:
- `docs/negocio/negocio-y-alcance.md`: producto, diferenciales, decisiones vigentes, fuera de alcance.
- `docs/requisitos/requisitos-funcionales.md`: RF1–RF63 (★ = módulo ingresos/egresos).
- `docs/arquitectura/adr/README.md`: ADR-0001…0016 y spikes, con implicaciones para el código.
- `docs/calidad/atributos-y-escenarios.md`: prioridades, escenarios y tácticas.
- `docs/restricciones/`: restricciones de negocio (legales: Ley 1581, DIAN, NIIF, laboral) y técnicas.
