# jjskaffee-backend

API REST del sistema de gestión financiera **multi-tenant** para cafeterías pequeñas e independientes, desarrollado por **JJSKaffee**.

> Repositorio hermano: [jjskaffee-frontend](https://github.com/JJSKaaffe/jjskaffee-frontend)

## Documentación

| Carpeta | Contenido |
| --- | --- |
| [`docs/`](docs/README.md) | Negocio y alcance, requisitos, calidad, restricciones y arquitectura del backend ([`backend-hexagonal.md`](docs/arquitectura/backend-hexagonal.md)) |
| [`doc/drivers`](doc/drivers/drivers.md) | Drivers arquitectónicos: funcionalidades significativas, restricciones y escenarios de calidad |
| [`doc/adr`](doc/adr) | Registros de decisiones de arquitectura (ADR-0001 a ADR-0016) |
| [`doc/spikes`](doc/spikes) | Spikes técnicos que validan las decisiones (SPIKE-01 a SPIKE-06) |
| [`docs/postman`](docs/postman) | Colección de Postman con el flujo de registrar ingreso |

## Arquitectura

**Monolito modular con módulos hexagonales** (ADR-0015): una sola aplicación Spring Boot, sin estado, dividida en módulos de negocio. Cada módulo tiene `dominio`, `servicios` (casos de uso y puertos) e `infraestructura` (controllers, repositorios y configuración).

| Módulo | Responsabilidad |
| --- | --- |
| `compartido` | Kernel: usuario autenticado, rol y permisos, excepciones, catálogo, unidad de trabajo (transacción + RLS), manejo de errores |
| `acceso` | Identidad (token de Firebase), cafeterías del usuario y rol en cada una |
| `ingresos` | Ventas, ingresos libres y catálogo de la pantalla de venta |
| `egresos` | Egresos |
| `movimientos` | Consulta de ingresos y egresos con filtros y totales |

`ArquitecturaTest` (ArchUnit) verifica en cada build las capas de cada módulo, que un módulo solo use los puertos de entrada de otro y que no haya ciclos.

## Flujo principal: registrar un ingreso

1. **Login**: el frontend obtiene el token de Firebase Authentication. El token solo identifica a la persona.
2. **Elegir cafetería**: `GET /api/acceso/cafeterias` devuelve las cafeterías del usuario con su rol en cada una (una persona puede ser administradora en una y cajera en otra, ADR-0016).
3. **Rol**: cada petición lleva el encabezado `X-Cafeteria-Id`; `GET /api/acceso/yo` devuelve el rol y los permisos en esa cafetería.
4. **Registrar**: una venta (`POST /api/ingresos`, el total lo calcula el servidor) o un ingreso libre (`POST /api/ingresos/libres`, monto + categoría, solo administrador). `GET /api/ingresos/catalogo` trae lo que muestra la pantalla de venta.
5. **Verlo reflejado**: `GET /api/movimientos` lo muestra con los totales del filtro.

Respuestas de acceso: sin token o token inválido → 401; falta `X-Cafeteria-Id` o no es UUID → 400; sin membresía activa en esa cafetería → 403. Errores en formato ProblemDetail (RFC 9457).

## Decisiones de arquitectura (ADR)

| ADR | Decisión | Spike |
| --- | --- | --- |
| [ADR-0001](doc/adr/adr-0001.md) | Autenticación delegada a **Firebase Authentication**; aislamiento por cafetería con RLS (el claim `id_cafeteria` lo reemplazó ADR-0016) | [SPIKE-01](doc/spikes/spike-01.md), [SPIKE-03](doc/spikes/spike-03.md) |
| [ADR-0002](doc/adr/adr-0002.md) | Todo movimiento financiero se registra dentro de una **transacción** (todo o nada) | — |
| [ADR-0003](doc/adr/adr-0003.md) | Categorías como entidad propia por cafetería, con archivado (no borrado) | — |
| [ADR-0004](doc/adr/adr-0004.md) | Reportes y dashboard con **consultas agregadas indexadas** en la base de datos | [SPIKE-06](doc/spikes/spike-06.md) |
| [ADR-0005](doc/adr/adr-0005.md) | Hosting con reinicio automático y monitoreo; alertas en tiempo real con **Firebase Cloud Messaging** | [SPIKE-04](doc/spikes/spike-04.md) |
| [ADR-0006](doc/adr/adr-0006.md) | **Monolito sin estado** (stateless) en lugar de microservicios | — |
| [ADR-0007](doc/adr/adr-0007.md) | `registrado_por_id` y `fecha_hora` se capturan **automáticamente en el backend** | — |
| [ADR-0008](doc/adr/adr-0008.md) | Interfaz con accesos directos, deshacer y **asistente de IA** en lenguaje natural | [SPIKE-02](doc/spikes/spike-02.md) |
| [ADR-0009](doc/adr/adr-0009.md) | Punto de venta operable con **teclado, mouse y pantalla táctil** | — |
| [ADR-0010](doc/adr/adr-0010.md) | Compatibilidad con **Chrome, Firefox y Edge** mediante estándares web | — |
| [ADR-0011](doc/adr/adr-0011.md) | IA, notificaciones y exportación vía proveedores externos; **sin pasarela de pagos** | [SPIKE-02](doc/spikes/spike-02.md), [SPIKE-05](doc/spikes/spike-05.md) |
| [ADR-0012](doc/adr/adr-0012.md) | Base de datos **PostgreSQL** con **Row Level Security**, en Cloud SQL | [SPIKE-03](doc/spikes/spike-03.md) |
| [ADR-0013](doc/adr/adr-0013.md) | Proveedor de nube: **Google Cloud Platform** | — |
| [ADR-0014](doc/adr/adr-0014.md) | Secretos centralizados en **Google Cloud Secret Manager** | — |
| [ADR-0015](doc/adr/adr-0015.md) | **Monolito modular con módulos hexagonales**, verificado con ArchUnit | — |
| [ADR-0016](doc/adr/adr-0016.md) | Cafetería elegida con **`X-Cafeteria-Id`** y rol por **membresía** (una persona, varias cafeterías) | — |

## Reglas no negociables

1. Toda operación de negocio corre en una transacción con la cafetería fijada para **Row Level Security** (`FORCE ROW LEVEL SECURITY` y un usuario de base de datos sin superusuario ni `BYPASSRLS`).
2. El rol (Administrador / Cajero) sale de la membresía en la cafetería elegida y se valida en el backend antes de cada acción.
3. La cafetería, el usuario que registra y la fecha los pone el servidor, nunca el cuerpo de la petición.
4. El backend es *stateless* y usa un pool de conexiones a la base de datos.
5. Los totales se calculan con SQL sobre los movimientos (`SUM`/`GROUP BY` con índices), nunca cargando todo a memoria.
6. El repositorio no contiene secretos: `.env` está en `.gitignore` y solo se versiona un `.env.example` sin valores (ADR-0014).

## Stack

- Java 27 y Spring Boot 4.1.1 (Web MVC, Validation, JDBC), Maven wrapper
- Persistencia: JDBC con `JdbcTemplate` (sin JPA)
- Base de datos: PostgreSQL 17 con Row Level Security (Cloud SQL, GCP)
- Autenticación: Firebase Authentication
- Notificaciones: Firebase Cloud Messaging
- Nube: Google Cloud Platform (Cloud Run) · Secretos: Google Cloud Secret Manager
- CI: GitHub Actions con pruebas, ArchUnit y Trivy

## Cómo correrlo

1. Crea la base con los scripts de [`db/`](db), en orden (`01` a `10`), con el usuario `postgres` (ver el encabezado de cada script).
2. Copia `.env.example` como `.env` y llena los valores.
3. Comandos (en Windows usa `.\mvnw.cmd`):
   - `./mvnw test`: pruebas unitarias y `ArquitecturaTest`, sin base de datos.
   - `./mvnw verify`: además, las pruebas de integración `*IT` contra PostgreSQL.
   - `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`: levanta la API en `http://localhost:8080` (sin el prefijo `/api`, que lo agrega el API Gateway).

## Equipo

JJSKaffee — Proyecto de Ingeniería de Software II.
