# JJSKaffee · Backend de ingresos y egresos

Sistema web SaaS multi-tenant para que cafeterías pequeñas e independientes de Colombia controlen sus ingresos y egresos, con reportes, comparación entre períodos y alertas configurables. Proyecto de Ingeniería de Software II (UCO). Este repositorio es el **backend**; Joel es responsable de la funcionalidad **registrar ingresos y egresos**. El frontend (Vue 3) vive en otro repo de la organización GitHub `JJSKaaffe`.

Responde y escribe comentarios, mensajes de commit y nombres de dominio **en español**, como el código existente.

## Stack
- Java 27, Spring Boot 4.1.1 (`spring-boot-starter-webmvc`, `spring-boot-starter-validation`), Maven wrapper. IDE: IntelliJ.
- Persistencia: **JDBC con `JdbcTemplate`, sin JPA** (ArchUnit lo prohíbe). PostgreSQL en Cloud SQL (GCP) con **Row Level Security**.
- Autenticación: **Firebase Authentication** (token JWT; `id_cafeteria` como custom claim). Nunca implementar login ni guardar contraseñas propias.
- Secretos: **Google Cloud Secret Manager** vía un componente `SecretsProvider`. Ningún secreto en el repo; solo `.env.example` sin valores.
- Notificaciones: Firebase Cloud Messaging. Entrada pública por un **API Gateway** (`/api/...`).
- CI en GitHub: **Trivy** y **ArchUnit**. Si Trivy reporta CVEs, se fijan versiones en `<properties>` del `pom.xml` (ya hay overrides de Tomcat y Jackson).

## Comandos
- `./mvnw test` (en Windows `mvnw.cmd test`): pruebas unitarias + `ArquitecturaTest`, sin base de datos. Correrlas siempre después de cambiar código.
- `./mvnw verify`: además corre las pruebas `*IT` contra PostgreSQL (necesitan la base creada y `.env` o las variables `JJSKAFFEE_DB_*`).
- `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`: levantar la API en local (lee `.env`; ver `.env.example`).
- `trivy fs --scanners vuln,secret,misconfig --severity HIGH,CRITICAL --ignore-unfixed .`: el mismo escaneo del CI (Trivy 0.75.0).

## Base de datos
Scripts en `db/` (se corren a mano en pgAdmin y en CI, en orden). Migración: `01_crear_usuario_y_base.sql` (rol `app_jjskaffee` sin superusuario ni BYPASSRLS + base `jjskaffee`) y `02_esquema.sql` (tablas, RLS forzado, vista `v_movimiento` con `security_invoker`, permisos mínimos). Seeds de prueba, siempre aparte de la migración: `03_seed_cafeterias_y_catalogo.sql` y `04_seed_usuarios.sql` (cafeterías A y B con los UUID de `DatosPrueba`). Después, `05_cafeteria_activa_e_idempotencia.sql` (migración: `cafeteria.activo`, `SELECT (id, activo)` para la app, `venta.clave_idempotencia` + `hash_solicitud` con UNIQUE por cafetería) y `06_seed_cafeteria_inactiva.sql` (seed: cafetería C inactiva). Orden: 01 → 02 → 03 → 04 → 05 → 06. No hay herramienta de migraciones: un cambio de esquema es un script nuevo numerado. Los nombres de tabla siguen el MER del equipo (p. ej. `usuarios`: administrador y cajeros). Las tablas de catálogo, `usuarios` y `cafeteria` (solo `id` y `activo`) son de solo lectura para la app; no hay `DELETE` en ninguna tabla.

## Arquitectura hexagonal (obligatoria)
Paquete raíz `co.edu.uco.jjskafee` (con una sola "f", no corregirlo):
- `dominio`: entidades en Java puro, enums y excepciones. Sin Spring, sin JPA, sin servlets.
- `servicios.casouso`: casos de uso en Java puro (**sin `@Service` ni nada de Spring, `java.sql` o Firebase**); se instancian en una clase de configuración de infraestructura.
- `servicios.input.ports` / `servicios.output.ports`: solo **interfaces**.
- `servicios.input.dto.request|response`: records; los request llevan validaciones `jakarta.validation`.
- `infraestructura.input`: controllers y filtros; solo usan **puertos de entrada** (nunca `casouso` ni `infraestructura.output`).
- `infraestructura.output`: repositorios JDBC, `UnidadDeTrabajo` con transacción + RLS, adaptador de Firebase, `SecretsProvider`.
- Nada puede depender de `infraestructura`; sin ciclos entre paquetes. Si un cambio rompe `ArquitecturaTest`, el cambio está mal, no la prueba.

**Estado**: dominio, casos de uso, puertos, DTOs e infraestructura (`MovimientosController`, `FirebaseAuthFilter`, `FirebaseAuthAdapter`, repositorios `Pg*`, `PgUnidadDeTrabajo`, `BeanConfig`) están hechos, con pruebas unitarias y de integración. El usuario sale del token (`uid` + claim `id_cafeteria`) y de la tabla `usuarios` (id, nombre, rol), leída con RLS. Pendiente: perfil `gcp` (Cloud Run + Cloud SQL) y `SecretsProvider` con Secret Manager (ADR-0014); hoy los secretos vienen de variables de entorno.

Detalle de capas, adaptadores pendientes y recorrido de una petición:
@docs/arquitectura/backend-hexagonal.md

## Reglas no negociables
- **Multi-tenant**: toda operación corre dentro de `UnidadDeTrabajo.ejecutar(usuario.idCafeteria(), ...)`. En BD: usuario de app limitado (nunca superusuario), `FORCE ROW LEVEL SECURITY` en toda tabla con datos de cafetería y la cafetería fijada por transacción. 0 % de fugas entre cafeterías.
- **Transacción todo o nada** para cada movimiento (venta con sus detalles juntos).
- **Trazabilidad**: `idCafeteria`, usuario que registra y `fechaHora` los pone el servidor (desde `UsuarioAutenticado` y un `Clock` inyectado). Nunca aceptarlos en el request.
- **El total de una venta se calcula en el servidor** con los precios del catálogo; nunca confiar en un total del navegador. El detalle guarda el precio unitario del momento.
- Egresos: solo ADMINISTRADOR, monto > 0, categoría de egreso activa. Ventas: cajero o administrador. Deshacer venta: solo quien la registró y dentro de 60 s. El cajero solo consulta lo que él registró.
- Categorías con historial se **archivan**, no se borran. Registros contables con **borrado lógico** y anulación con motivo.
- Agregados y totales con SQL (`SUM`/`GROUP BY`) e índices (`id_cafeteria`, fecha, categoría); nunca cargar todo a memoria para sumar. Paginar historiales.
- Dinero en `BigDecimal`, IDs `UUID`, tiempo `Instant`. Entidades con fábrica que valida (`crear`/`registrar`) y `reconstruir` para leer de BD.
- Metas de tiempo: registrar ingreso o egreso ≤ 3 s (venta ideal 1–2 s); consultar movimientos con filtros ≤ 4 s.

## Decisiones vigentes cuando los documentos se contradicen
Hay textos antiguos que dicen "una sola cafetería", "sin IA", "sin nómina", "MVC" o "dashboard en tiempo real". Lo vigente es: **multi-tenant**, **sí IA** (explica alertas que se calculan por reglas), **nómina básica**, **inventario incluido**, **dashboard calculado al consultar**, **sin pasarela de pagos** (registro manual), **monolito sin estado con backend hexagonal**. Ver `docs/negocio/negocio-y-alcance.md`.

## Cómo trabajar en este repo
- Antes de implementar algo, revisa el RF y los ADR relacionados; si el código y la documentación no coinciden, avisa en vez de elegir por tu cuenta.
- Cada caso de uso nuevo lleva prueba con los fakes de `RepositoriosEnMemoria`; cada regla de dominio, prueba en `dominio/*Test`.
- El diagrama hexagonal muestra "Comandos" entre DTO y caso de uso, pero el código no los usa todavía: pregunta antes de introducirlos.

## Documentación (léela cuando la tarea lo requiera)
Índice completo en `docs/README.md`:
- `docs/negocio/negocio-y-alcance.md`: producto, diferenciales, decisiones vigentes, fuera de alcance.
- `docs/requisitos/requisitos-funcionales.md`: RF1–RF63 (★ = módulo ingresos/egresos).
- `docs/arquitectura/adr/README.md`: ADR-0001…0014 y spikes, con implicaciones para el código.
- `docs/calidad/atributos-y-escenarios.md`: prioridades, escenarios y tácticas.
- `docs/restricciones/`: restricciones de negocio (legales: Ley 1581, DIAN, NIIF, laboral) y técnicas.
