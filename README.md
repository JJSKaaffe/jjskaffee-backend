# JJSKaffee · backend modular (respaldo de emergencia)

Backend de **registro de ingresos y egresos** con arquitectura de **monolito modular**.
Es un proyecto aparte: no toca el repositorio `jjskaffee-backend` (hexagonal).
Usa **la misma base de datos** (`db/jjskaffee_completo.sql`, sin cambios) y el mismo stack:
Java 27 · Spring Boot 4.1.1 · JDBC (sin JPA) · PostgreSQL con RLS · Firebase Auth · Trivy · ArchUnit.

## Módulos

| Módulo | Qué hace | Tablas que le pertenecen | Usa de otros módulos |
|---|---|---|---|
| `compartido` | Usuario autenticado, transacción + RLS, mensajes, errores HTTP, CORS, trazas | lee `usuario` | — |
| `catalogo` | Productos, adiciones y categorías (solo lectura) | `categoria`, `producto`, `producto_adicion` | compartido |
| `ingresos` | Ventas con adiciones (RF24–RF27) y deshacer en 60 s (ADR-0008) | `venta`, `detalle_venta` | `catalogo.api` |
| `egresos` | Gastos, solo administrador (RF14, RF18) | `egreso` | `catalogo.api` |
| `movimientos` | Consulta con totales y paginación (ADR-0004) | lee la vista `v_movimiento` | compartido |

Cada módulo tiene las mismas capas:

```
<modulo>/
  api/              contrato público (solo catalogo lo necesita): interfaces + records
  dominio/          reglas del negocio, Java puro, y el enum Mensajes<Módulo> con las claves
  aplicacion/       casos de uso (Java puro) + puertos/entrada + puertos/salida + comandos
  infraestructura/
    web/            controladores y DTO request/response
    persistencia/   repositorios Pg* con JdbcTemplate
    salida/         adaptadores hacia otros módulos (por su api)
    configuracion/  @Configuration que crea los beans del módulo
```

`ArquitecturaTest` (ArchUnit) hace cumplir: un módulo solo usa la `api` de otro, `compartido` no depende de
ningún módulo, sin ciclos, dominio/aplicación sin Spring ni JDBC, controladores solo con puertos de entrada,
puertos = interfaces, sin JPA, JDBC solo en persistencia y Firebase solo en seguridad.

## Mensajes (nada quemado)

* Todos los textos para el usuario están en `src/main/resources/i18n/mensajes.properties`.
* El código solo usa **claves**: enums `MensajesCompartidos`, `MensajesIngresos`, `MensajesEgresos`, `MensajesMovimientos`.
  Las excepciones reciben la clave (`new ReglaNegocioException(MensajesEgresos.MONTO_INVALIDO)`), nunca un texto.
* Las validaciones de los request usan claves: `@NotNull(message = "{validacion.egreso.monto.obligatorio}")`.
* `MensajesTest` falla si falta una clave, si está vacía o si sobra una que nadie usa.
* Errores en formato RFC 9457, con `codigo` para que el frontend no dependa del texto:

```json
{ "status": 422, "title": "Regla de negocio", "detail": "El monto debe ser mayor que 0.",
  "codigo": "egresos.monto.invalido", "instante": "2026-10-14T23:00:00Z" }
```

## Endpoints

Todos bajo `/api`, con `Authorization: Bearer <token de Firebase>`. La cafetería, el usuario y la fecha
los pone el servidor, nunca el navegador.

| Método y ruta | Quién | Respuesta |
|---|---|---|
| `GET /api/catalogo/productos` | todos | productos vendibles con sus adiciones permitidas |
| `GET /api/catalogo/categorias?tipo=EGRESO` | todos | categorías activas |
| `POST /api/ingresos` | cajero, admin | 201 venta con total calculado en el servidor |
| `POST /api/ingresos/{idVenta}/deshacer` | quien la registró, ≤ 60 s | venta anulada |
| `POST /api/egresos` | admin | 201 egreso |
| `GET /api/movimientos?desde=&hasta=&tipo=&idCategoria=&pagina=&tamano=` | admin ve todo, cajero solo lo suyo | lista + totales + página |
| `GET /actuator/health` | público | estado del servicio |

Ejemplo de venta: `{"items":[{"idProducto":"b0000000-0000-0000-0000-000000000003","cantidad":1,"adiciones":[{"idProducto":"b0000000-0000-0000-0000-000000000011"}]}]}`

## Cómo correrlo en tu PC

1. **Base de datos (pgAdmin)**: si ya la tienes del otro backend, no hagas nada; es la misma.
   Si no: crea la base `jjskaffee`, abre Query Tool y ejecuta `db/jjskaffee_completo.sql`.
   Opcional: `db/verificar_jjskaffee.sql` debe mostrar OK 1 … OK 9.
2. Copia `.env.example` como `.env` y pon `JJSKAFFEE_DB_CONTRASENA` (en desarrollo: la del script).
3. En IntelliJ: Run configuration de `JjSkafeeModularApplication` con **Active profiles = `local`**.
4. Prueba sin Firebase (solo perfil local, modo desarrollo):
   `Authorization: Bearer dev:11111111-1111-1111-1111-111111111111:firebase-cajero-demo`
   (o `firebase-admin-demo` para el administrador).

> El backend hexagonal y este usan el puerto 8080: no los corras al tiempo, o agrega la línea `server.port=8081`
> al `.env`.

## Pruebas, ArchUnit y Trivy

```bash
./mvnw verify                      # unitarias + ArquitecturaTest + MensajesTest
trivy fs --config trivy.yaml .     # mismo criterio que en GitHub (falla con CRITICAL/HIGH o secretos)
```

Pruebas de integración contra tu PostgreSQL (se omiten si no defines las variables; cada prueba se revierte,
así que no ensucian tu base):

```bash
JJSKAFFEE_PRUEBAS_DB_URL=jdbc:postgresql://localhost:5432/jjskaffee?currentSchema=jjskaffee
JJSKAFFEE_PRUEBAS_DB_USUARIO=app_jjskaffee
JJSKAFFEE_PRUEBAS_DB_CONTRASENA=...
```

GitHub Actions (`.github/workflows/calidad-seguridad.yml`):
* **Pruebas**: levanta `postgres:17`, carga `db/jjskaffee_completo.sql`, corre `db/verificar_jjskaffee.sql`
  y luego `./mvnw verify` en Java 27 (incluye integración y aislamiento A/B).
* **Trivy** `aquasecurity/trivy-action@v0.36.0` con Trivy `v0.75.0`: SARIF a Security + paso que falla con CRITICAL/HIGH/secretos.
* **CodeQL** con `build-mode: none` (CodeQL aún no lista Java 27).
* `dependabot.yml` para Maven y Actions. Los overrides de `tomcat.version` y `jackson-bom.version` del `pom.xml`
  son los mismos que pidió Trivy en el backend hexagonal.

## HTTPS

El backend **no termina TLS por sí mismo**, y así debe ser: el certificado lo pone el proveedor
y la aplicación recibe HTTP plano en `$PORT`. Lo único que necesita es creerle al proxy, y eso ya
está en `application.yml`:

```yaml
server:
  port: ${PORT:8080}
  forward-headers-strategy: framework   # respeta X-Forwarded-Proto -> las URLs salen en https
```

Sin esa línea, la cabecera `Location` de un 201 y el campo `instance` de los errores saldrían en
`http://` y el navegador los bloquearía por contenido mixto.

### En Cloud Run (producción)

`https://<servicio>-<hash>.run.app` con certificado administrado por Google: no hay que comprarlo
ni renovarlo. El `Dockerfile` (multi-etapa, usuario sin privilegios, JRE sin compilador) y
`.github/workflows/despliegue.yml` ya están listos. Antes del primer despliegue hay que crear los
secretos y variables que ese archivo documenta en su encabezado.

### En tu PC (opcional, perfil `https`)

Genera una sola vez el certificado autofirmado, en la raíz del proyecto:

```bash
keytool -genkeypair -alias jjskaffee-local -keyalg RSA -keysize 2048 \
  -storetype PKCS12 -keystore certificado-local.p12 -validity 365 \
  -storepass cambiar_en_desarrollo \
  -dname "CN=localhost, OU=JJSKaffee, O=UCO, L=Rionegro, ST=Antioquia, C=CO" \
  -ext "SAN=dns:localhost,ip:127.0.0.1"
```

Luego corre con **Active profiles: `local,https`** y el backend queda en `https://localhost:8443`.
El navegador avisa que el certificado no es de confianza (lo firmaste tú): se acepta y sigue.
El `.p12` está en el `.gitignore`: cada quien genera el suyo, nunca se sube.

## Trazabilidad y observabilidad

* `spring-boot-starter-opentelemetry` pone `traceId`/`spanId` en el MDC; `FiltroAutenticacion` agrega
  `idCafeteria`, `idUsuario` y `rol`. Cada línea de log queda así: `[traceId,spanId] [caf=… usr=… rol=…]`.
* Fechas en UTC-0: JVM, `Clock.systemUTC()`, sesión de Hikari y el rol de la BD.

## Diferencias con el backend hexagonal

* Mismo dominio y mismas reglas; ahora organizado por módulos con fronteras verificadas.
* Deshacer una venta ahora guarda `usuario_autorizador` y `anulada_en`, que el CHECK de la tabla `venta` exige.
* Los textos salieron del código a `mensajes.properties` (incluida la descripción "Venta" de los movimientos).
* La consulta de movimientos calcula totales con `SUM … FILTER` en la BD y pagina (ADR-0004).
* Pendiente igual que en el otro: la anulación por el administrador después de 60 s y RF15/RF28 (editar/eliminar).
