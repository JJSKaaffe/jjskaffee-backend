# Backend de ingresos y egresos · arquitectura hexagonal

> Fuentes: diagrama "Hexagonal ingresos y egresos.png" (copia en `img/hexagonal-ingresos-egresos.png`), código de `jjskaffee-backend-listo` subido al Project y `ArquitecturaTest.java`.

Alcance: RF14 (egresos), RF18 (categorías), RF24–RF27 (ventas con adiciones), RF9 y RF38 (consulta de movimientos con totales).

## Capas y paquetes (`co.edu.uco.jjskafee`)
| Capa | Paquete | Contenido | Puede depender de |
|---|---|---|---|
| Dominio | `dominio` | Entidades Java puro: `Venta`, `DetalleVenta`, `Egreso`, `Categoria`, `Producto`, `Movimiento`, `ResultadoMovimientos`, `UsuarioAutenticado`; `enums` (`Rol`, `TipoMovimiento`, `TipoCategoria`, `TipoProducto`, `EstadoVenta`); `excepciones` (`ReglaNegocioException`, `SinPermisoException`, `RecursoNoEncontradoException`) | Transversal |
| Servicios | `servicios.casouso` | Casos de uso: `RegistrarIngreso`, `RegistrarEgreso`, `ConsultarMovimientos` | Dominio, Transversal |
| | `servicios.input.ports` | Puertos de entrada (interfaces): `RegistrarIngresoUseCase`, `RegistrarEgresoUseCase`, `ConsultarMovimientosUseCase` | |
| | `servicios.input.dto.request` / `.response` | `IngresoRequest`, `ItemRequest`, `AdicionRequest`, `EgresoRequest`, `FiltroMovimientosRequest` / `IngresoResponse`, `ItemResponse`, `AdicionResponse`, `EgresoResponse`, `MovimientoResponse`, `MovimientosResponse`, `TotalesResponse` | |
| | `servicios.output.ports` | Puertos de salida (interfaces): `VentaRepository`, `EgresoRepository`, `CatalogoRepository`, `CafeteriaRepository`, `MovimientoRepository`, `UnidadDeTrabajo`, `VerificadorDeToken` | |
| Infraestructura | `infraestructura.input` | Adaptadores de entrada: `MovimientosController`, `ManejadorDeErrores`, `FirebaseAuthFilter` | Servicios, Dominio, Transversal |
| | `infraestructura.output` | Adaptadores de salida: repositorios `Pg*`, `PgUnidadDeTrabajo`, `FirebaseAuthAdapter` (secretos: pendiente) | |
| | `infraestructura.configuracion` | `BeanConfig` (crea casos de uso y adaptadores), `FirebaseConfig`, `CorsConfig` | |
| Transversal | `transversal` | Utilidades comunes (opcional) | — |

## Reglas que verifica ArchUnit (`ArquitecturaTest`) — no romperlas
1. Capas: Infraestructura no es accedida por nadie; Servicios solo por Infraestructura; Dominio solo por Servicios e Infraestructura.
2. El dominio no depende de `org.springframework..`, `jakarta.persistence..` ni `jakarta.servlet..`.
3. Los servicios no dependen de `org.springframework..`, `java.sql..` ni `com.google.firebase..` → **casos de uso en Java puro, sin `@Service`**; se instancian en una clase de configuración (`BeanConfig`) de infraestructura.
4. `infraestructura.input` no depende de `infraestructura.output` ni de `servicios.casouso` → los controllers hablan **solo con los puertos de entrada**.
5. Todo lo que esté en `servicios.input.ports` y `servicios.output.ports` debe ser **interface**.
6. Sin ciclos entre paquetes.
7. **Sin JPA** en ningún lado: persistencia con **JDBC (`JdbcTemplate`)**.

## Adaptadores
**Entrada**
- `MovimientosController` (`@RestController`), expuesto por el API Gateway con el prefijo `/api`:
  - `POST /ingresos` (encabezado opcional `Idempotency-Key: <UUID>`) → 201 `IngresoResponse`
  - `POST /ingresos/{idVenta}/deshacer` → 204
  - `POST /egresos` → 201 `EgresoResponse`
  - `GET /movimientos?desde&hasta&tipo&idCategoria&pagina&tamano` → 200 `MovimientosResponse` (página ≤ 50 y totales de todo el filtro)
- `FirebaseAuthFilter`: exige `Authorization: Bearer <token>`, lo valida con `VerificadorDeToken` y deja `UsuarioAutenticado` en la petición; sin token válido responde 401.
- `ManejadorDeErrores`: ProblemDetail (RFC 9457). 400 datos inválidos (con `errores` por campo), 401, 403 `SinPermisoException`, 404 `RecursoNoEncontradoException`, 422 `ReglaNegocioException`, 500 sin detalles internos.

**Salida**
- `PgVentaRepository` (guarda con `INSERT ... ON CONFLICT (id_cafeteria, clave_idempotencia) DO NOTHING`), `PgEgresoRepository`, `PgCatalogoRepository`, `PgCafeteriaRepository` (lee `cafeteria.activo`), `PgMovimientoRepository` (totales con `SUM ... FILTER` y página con `LIMIT/OFFSET` sobre `v_movimiento`): JDBC sobre PostgreSQL.
- `PgUnidadDeTrabajo`: abre la transacción y ejecuta `set_config('app.current_cafeteria', id, true)` para RLS.
- `FirebaseAuthAdapter` (implementa `VerificadorDeToken`): verifica el token, toma `id_cafeteria` del claim y busca `id`, `nombre` y `rol` en la tabla `usuarios` (con RLS de esa cafetería, solo usuarios activos).
- Pendiente: `SecretsProvider` (Google Cloud Secret Manager) con el perfil `gcp`.

**Externos**: PostgreSQL en Cloud SQL (tablas `cafeteria`, `usuarios`, `venta`, `detalle_venta`, `egreso`, `categoria`, `producto`, `producto_adicion` con RLS por cafetería; esquema en `db/02_esquema.sql` y `db/05_cafeteria_activa_e_idempotencia.sql`, seeds de prueba en `db/03`, `db/04` y `db/06`), Firebase Authentication, Google Cloud Secret Manager.

## Recorrido: registrar un egreso
1. El administrador llena el formulario (Vue 3).
2. La app envía `POST /api/egresos` con el token.
3. El API Gateway enruta al backend.
4. `FirebaseAuthFilter` verifica el token → `UsuarioAutenticado`.
5. Spring convierte el JSON en `EgresoRequest`.
6. `RegistrarEgreso` ejecuta el caso de uso.
7. Se crea la entidad `Egreso` y valida sus reglas.
8. Llama al puerto `EgresoRepository`.
9. Spring inyecta la implementación (configuración).
10. `PgEgresoRepository` hace el `INSERT` (JdbcTemplate).
11. PostgreSQL guarda con RLS por cafetería.
12. El controller responde `EgresoResponse` (JSON).

> Nota: el diagrama dibuja "Comandos" (`RegistrarIngresoComando`, `RegistrarEgresoComando`) entre el DTO y el caso de uso. El código actual **no** tiene comandos: los casos de uso reciben directamente los DTO request de `servicios.input.dto.request`. Antes de agregar comandos, preguntar a Joel si quiere alinear el código con el diagrama o el diagrama con el código.

## Registrar ingreso: decisión y validaciones
**Decisión (esta versión):** todo ingreso se registra como una **venta de productos del menú** (RF24–RF27). No hay ingresos "libres" (monto + categoría de ingreso): el monto siempre sale de los productos del catálogo. Por eso `POST /ingresos` recibe productos y adiciones, nunca un monto, y en la BD el ingreso es la tabla `venta` (con `detalle_venta`). Si más adelante se necesitan otros ingresos, será un caso de uso aparte, sin cambiar este.

Validaciones de `RegistrarIngreso` (en orden). Ninguna está en el controller: este solo pasa el cuerpo, el encabezado `Idempotency-Key` y el usuario del token.

| # | Regla | Dónde | Respuesta |
|---|---|---|---|
| 1 | Token válido; cafetería, usuario y fecha salen del servidor (nunca del cuerpo) | `FirebaseAuthFilter`, `UsuarioAutenticado`, `Clock` | 401 |
| 2 | La cafetería del usuario está **activa** (`cafeteria.activo`; si el RLS no la deja ver, cuenta como inactiva) | `RegistrarIngreso` + `CafeteriaRepository` | 422 |
| 3 | **Idempotencia**: con `Idempotency-Key` (UUID) repetido en la misma cafetería y el mismo contenido se devuelve la venta ya registrada (201, mismo `idVenta`, aunque esté anulada) | `RegistrarIngreso` + `VentaRepository.buscarPorClaveIdempotencia` | 201 |
| 4 | La misma clave con **otro contenido** (otro `hash_solicitud`) se rechaza | `Venta.verificarMismaSolicitud` | 422 |
| 5 | La clave no es un UUID | Spring (conversión del encabezado) | 400 |
| 6 | Al menos un producto | `IngresoRequest` y `Venta.validarParaRegistrar` | 400 / 422 |
| 7 | Cada producto existe en el catálogo de la cafetería (RLS) | `RegistrarIngreso` | 404 |
| 8 | El producto base es `BASE` y está disponible; cada adición es `ADICION`, está disponible y está permitida para ese base (`producto_adicion`) | `Venta.agregar` | 422 |
| 9 | **Precio > 0** y con máximo 2 decimales, en el producto base y en cada adición | `Venta.agregar` | 422 |
| 10 | **Cantidad entera de 1 a 99**, en el producto base y en cada adición (las adiciones repetidas se suman y la suma también debe ser ≤ 99). `1.5` no se trunca: responde 400 (`spring.jackson.deserialization.accept-float-as-int=false`) | `ItemRequest`/`AdicionRequest` (`@Min`/`@Max`) y `Venta.agregar` | 400 / 422 |
| 11 | **Máximo 50 líneas** por venta: cada producto base y cada adición distinta es una línea. Si un producto no cabe, no queda a medias | `Venta.agregar` (`MAXIMO_LINEAS`); `@Size` en el DTO solo evita cuerpos enormes | 400 / 422 |
| 12 | **Total > 0, máximo 2 decimales y sin errores de redondeo**: Σ precio × cantidad en `BigDecimal` exacto (sin `double` ni `setScale`); un valor con más de 2 decimales se rechaza, no se redondea | `Venta.validarParaRegistrar` | 422 |
| 13 | Venta y detalles se guardan **todo o nada**, con RLS de la cafetería | `UnidadDeTrabajo` | — |

Detalles de la idempotencia:
- Columnas `venta.clave_idempotencia` (uuid) y `venta.hash_solicitud` (SHA-256 en hexadecimal de productos, cantidades y adiciones en orden; una adición sin cantidad cuenta como 1), con `UNIQUE (id_cafeteria, clave_idempotencia)` y un `CHECK` que exige las dos juntas (`db/05`). Sin encabezado, ambas quedan `NULL` y cada petición es una venta nueva.
- Dos peticiones iguales al mismo tiempo: la segunda no choca con error; el `INSERT ... ON CONFLICT DO NOTHING` no inserta, `guardar` devuelve `false` y el caso de uso responde con la venta que ganó.
- La misma clave en otra cafetería es otra venta (la UNIQUE y el RLS son por cafetería).

Fechas: `fecha_hora` (y `cafeteria.creada_en`) son `timestamptz`; el `Clock` del servidor es UTC y `ConversionesSql` escribe `OffsetDateTime` en UTC. PostgreSQL guarda el instante absoluto, así que la zona de la sesión no cambia lo guardado; la hora de Colombia solo se usa para interpretar los filtros `desde`/`hasta`.

Permisos: para la regla 2, `db/05` le da a `app_jjskaffee` `SELECT (id, activo)` sobre `cafeteria` (antes no tenía nada sobre esa tabla, ver comentario en `db/05`). Se descartó una función `SECURITY DEFINER` porque correría como `postgres` y se saltaría el RLS.

## Reglas de negocio implementadas en el dominio
- **Venta** (ingreso): la pueden registrar cajero y administrador. Total = Σ(producto base + adiciones) calculado **siempre en el servidor con precios del catálogo**; nunca se acepta un total del navegador. Validaciones completas en la sección anterior. Adiciones repetidas se suman. **Deshacer**: solo quien la registró, dentro de 60 s, si no estaba anulada; después la anula el administrador.
- **Egreso**: solo el **administrador**; monto > 0; categoría de egreso y activa; descripción obligatoria (máx. 200); comprobante opcional (máx. 50).
- **Consulta de movimientos** (`FiltroMovimientos`): `desde` ≤ `hasta` (fechas en hora de Colombia, `hasta` incluye el día completo); página ≥ 0 y tamaño 1–50 (por defecto 20); filtros por tipo y categoría; el administrador ve todo; el cajero **solo lo que él registró**; devuelve la página y los totales (ingresos, egresos, balance) de todo el filtro, calculados con SQL.
- Patrones: entidades con fábrica (`crear` / `registrar`) que valida, y `reconstruir` para el repositorio; IDs `UUID`; dinero en `BigDecimal`; tiempo con `Instant` y `Clock` inyectado.

## Pruebas existentes
- Unitarias (`./mvnw test`): `dominio/{Venta,Egreso,FiltroMovimientos}Test`, `servicios/casouso/{RegistrarIngreso,RegistrarEgreso,ConsultarMovimientos}Test` con `RepositoriosEnMemoria` (fakes de los puertos), `FirebaseAuthFilterTest`, `FirebaseAuthAdapterTest`, `DatosPrueba` y `ArquitecturaTest`.
- Integración contra PostgreSQL (`./mvnw verify`, paquete `integracion`): `AislamientoEntreCafeteriasIT` (A no ve ni escribe datos de B, rol sin superusuario/BYPASSRLS, RLS forzado en toda tabla, vista con `security_invoker`), `IngresosYEgresosPostgresIT`, `RegistrarIngresoValidacionesIT` (cafetería inactiva con el seed `db/06`, permisos sobre `cafeteria`, idempotencia con la UNIQUE real, fechas `timestamptz` en UTC), `TransaccionTodoONadaIT` y `MovimientosControllerIT` (HTTP completo). Usan un reloj fijo en 2099 y deshacen sus cambios.

## Calidad en CI
GitHub con **Trivy** (vulnerabilidades; el `pom.xml` ya fuerza `tomcat.version` y `jackson-bom.version` por CVEs reportados) y **ArchUnit**.
