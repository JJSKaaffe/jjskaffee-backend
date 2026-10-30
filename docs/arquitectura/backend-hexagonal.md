# Backend · monolito modular con módulos hexagonales

> Fuentes: diagrama "Hexagonal ingresos y egresos.png" (copia en `img/hexagonal-ingresos-egresos.png`), el código de este repo y `ArquitecturaTest.java`. Decisiones: ADR-0015 (monolito modular), ADR-0016 (cafetería por encabezado y membresías) y ADR-0017 (API Gateway), en `doc/adr/`.

Alcance: RF7 y RF9 (multi-cafetería y aislamiento), RF14 (egresos), RF16–RF18 (consulta y categorías), RF24–RF27 (ventas con adiciones), ingresos libres, RF38 (consulta de movimientos con totales) y RF40 (permisos por rol).

## Módulos (`co.edu.uco.jjskafee`)
Una sola aplicación Spring Boot (un jar, un servicio en Cloud Run), dividida en módulos de negocio. Cada módulo es una hexagonal.

| Módulo | Qué tiene |
|---|---|
| `compartido` | Kernel: `UsuarioAutenticado`, `Rol` y `Permiso` (los permisos de cada rol se definen en `Rol.permisos()`), excepciones, `Producto`, `Categoria`, `TipoProducto`, `TipoCategoria`, `UnidadDeTrabajo` + `PgUnidadDeTrabajo`, `CatalogoRepository` + `PgCatalogoRepository`, `ConversionesSql`, `ManejadorDeErrores`, `AtributosDePeticion`, `Encabezados`, `CorsConfig`, `CompartidoConfig` (reloj). También las carpetas reservadas (`.gitkeep`) para adaptadores futuros. |
| `acceso` | Identidad, cafeterías del usuario y rol: `Identidad`, `CafeteriaDelUsuario`, `AutenticarUsuario`, `ConsultarAcceso`, `VerificadorDeToken` + `FirebaseAuthAdapter`, `MembresiaRepository` + `PgUsuarioRepository`, `FirebaseAuthFilter`, `AccesoController`, `FirebaseConfig`, `AccesoConfig`. |
| `ingresos` | Ventas (`Venta`, `DetalleVenta`, `EstadoVenta`), ingresos libres (`IngresoLibre`) y catálogo de venta (`ProductoDelMenu`): `RegistrarIngreso`, `RegistrarIngresoLibre`, `ConsultarCatalogoDeVenta`, `PgVentaRepository`, `PgIngresoLibreRepository`, `PgCafeteriaRepository`, `PgCatalogoDeVentaRepository`, `IngresosController`, `IngresosConfig`. |
| `egresos` | `Egreso`, `RegistrarEgreso`, `PgEgresoRepository`, `EgresosController`, `EgresosConfig`. Solo se movió de paquete; la lógica no cambió. |
| `movimientos` | `Movimiento`, `FiltroMovimientos`, `ResultadoMovimientos`, `TipoMovimiento`, `ConsultarMovimientos`, `PgMovimientoRepository` (lee la vista `v_movimiento`), `MovimientosController`, `MovimientosConfig`. |

## Capas dentro de cada módulo
| Capa | Paquete | Contenido | Puede depender de |
|---|---|---|---|
| Dominio | `<modulo>.dominio` | Entidades en Java puro, `enums`, `excepciones` | Dominio de `compartido` |
| Servicios | `<modulo>.servicios.casouso` | Casos de uso en Java puro (sin `@Service`); los crea la configuración del módulo | Dominio, puertos, DTO |
| | `<modulo>.servicios.input.ports` | Puertos de entrada (interfaces) | |
| | `<modulo>.servicios.input.dto.request` / `.response` | Records; los request llevan validaciones `jakarta.validation` | |
| | `<modulo>.servicios.output.ports` | Puertos de salida (interfaces) | |
| Infraestructura | `<modulo>.infraestructura.input` | Controllers y filtros: solo usan puertos de entrada | Servicios, Dominio |
| | `<modulo>.infraestructura.output` | Repositorios JDBC (`JdbcTemplate`) y adaptadores externos | |
| | `<modulo>.infraestructura.configuracion` | Crea los casos de uso y adaptadores del módulo | |

## Reglas que verifica ArchUnit (`ArquitecturaTest`) — no romperlas
1. Capas por módulo: infraestructura no la usa nadie; servicios solo la usa infraestructura; dominio solo lo usan servicios e infraestructura.
2. El dominio no depende de `org.springframework..`, `jakarta.persistence..` ni `jakarta.servlet..`.
3. Los servicios no dependen de `org.springframework..` (tampoco de Spring web), `java.sql..` ni `com.google.firebase..`.
4. `*.infraestructura.input` no depende de `*.infraestructura.output` ni de `*.servicios.casouso`.
5. Todo lo que está en `*.servicios.input.ports` y `*.servicios.output.ports` es **interface**.
6. **Sin JPA** en ningún lado.
7. Un módulo solo usa de otro lo que está en `<modulo>.servicios.input..` (puertos de entrada y DTO). `compartido` lo pueden usar todos.
8. `compartido` no depende de ningún módulo.
9. Sin ciclos entre módulos ni entre paquetes.

Si un cambio rompe `ArquitecturaTest`, el cambio está mal, no la prueba.

## Endpoints
El API Gateway los expone con el prefijo `/api` (ver "API Gateway" más abajo). Todos llevan `Authorization: Bearer <token de Firebase>` y `X-Cafeteria-Id: <UUID>`, salvo `GET /acceso/cafeterias`, que solo lleva el token.

| Método y ruta | Quién | Respuesta |
|---|---|---|
| `GET /acceso/cafeterias` | Cualquier persona con sesión | 200: `[{idCafeteria, nombre, rol, activa}]` |
| `GET /acceso/yo` | Miembro de la cafetería | 200: `{id, nombre, rol, idCafeteria, permisos}` |
| `GET /ingresos/catalogo` | Miembro de la cafetería | 200: productos base vendibles con adiciones y precios; categorías de INGRESO activas solo para el administrador |
| `POST /ingresos` (opcional `Idempotency-Key: <UUID>`) | Cajero o administrador | 201 `IngresoResponse` |
| `POST /ingresos/{idVenta}/deshacer` | Quien registró la venta, dentro de 60 s | 204 |
| `POST /ingresos/libres` (opcional `Idempotency-Key: <UUID>`) | Administrador | 201 `IngresoLibreResponse` |
| `POST /egresos` | Administrador | 201 `EgresoResponse` |
| `GET /movimientos?desde&hasta&tipo&idCategoria&pagina&tamano` | Administrador: todo; cajero: solo lo suyo | 200 `MovimientosResponse` (página ≤ 50 y totales de todo el filtro) |

**Acceso (`FirebaseAuthFilter`, ADR-0016)**: toma el token de `X-Forwarded-Authorization` si viene (detrás del API Gateway) y si no de `Authorization`, lo verifica (`VerificadorDeToken` solo devuelve el `uid`), lee `X-Cafeteria-Id` y busca la membresía activa de ese `uid` en esa cafetería con RLS. Sin token o token inválido → 401; falta el encabezado o no es UUID → 400; sin membresía activa (o la cafetería no existe) → 403. Deja `UsuarioAutenticado` en la petición (`AtributosDePeticion.USUARIO`) con el rol de esa cafetería.

**Errores (`ManejadorDeErrores`)**: ProblemDetail (RFC 9457). 400 datos inválidos (con `errores` por campo), 401, 403 `SinPermisoException`, 404 `RecursoNoEncontradoException`, 422 `ReglaNegocioException`, 500 sin detalles internos.

## API Gateway (ADR-0017)
Google Cloud API Gateway es la única entrada pública; Cloud Run queda privado (`--no-allow-unauthenticated`, `roles/run.invoker` solo para la cuenta `gateway-invoker`). El gateway no tiene código: es `infra/api-gateway/openapi.yaml` (OpenAPI 2.0).
- Una ruta por endpoint del backend, con el prefijo `/api`, agrupadas con `tags` por módulo (`acceso`, `ingresos`, `egresos`, `movimientos`); no se renombran ni se combinan.
- `securityDefinitions.firebase` (emisor `https://securetoken.google.com/jjskafee`, audiencia `jjskafee`) y `security: [firebase]` global; cada ruta tiene su `OPTIONS` con `security: []` para que el preflight de CORS llegue al backend.
- `x-google-backend` con la URL de Cloud Run (marcador `CLOUD_RUN_URL`), `path_translation: APPEND_PATH_TO_ADDRESS`, `jwt_audience` y `deadline: 15`. La ruta llega completa (`/api/...`), por eso el perfil `gcp` usa `server.servlet.context-path=/api`; en local no hay prefijo.
- El gateway llama a Cloud Run con el token de su cuenta de servicio en `Authorization` y copia el del usuario a `X-Forwarded-Authorization`; `FirebaseAuthFilter` prefiere ese encabezado. El backend vuelve a verificar el token y la membresía: no se confía solo en el gateway.
- `X-Cafeteria-Id` e `Idempotency-Key` pasan sin cambios. Permisos por rol y cafetería los revisa el backend.
- `ContratoApiGatewayTest` (en `./mvnw test`) verifica que cada endpoint de los controllers esté en el yaml con su método y su `OPTIONS`, que toda operación salvo `OPTIONS` exija `firebase` y que el yaml no tenga rutas que el backend no atienda.
- Despliegue paso a paso: `docs/despliegue/api-gateway.md`. La copia con la URL real (`openapi.desplegado.yaml`) está en `.gitignore`.

## Adaptadores de salida
- `PgUnidadDeTrabajo`: abre la transacción y fija `app.current_cafeteria` (para toda operación de negocio) o `app.current_uid` (solo para "mis cafeterías") con `set_config(..., true)`. La misma sentencia que fija una borra la otra: nunca quedan las dos activas.
- `PgUsuarioRepository` (membresías), `PgVentaRepository` y `PgIngresoLibreRepository` (guardan con `INSERT ... ON CONFLICT (id_cafeteria, clave_idempotencia) DO NOTHING`), `PgEgresoRepository`, `PgCatalogoRepository`, `PgCatalogoDeVentaRepository` (productos y adiciones en una sola consulta), `PgCafeteriaRepository` (lee `cafeteria.activo`), `PgMovimientoRepository` (totales con `SUM ... FILTER` y página con `LIMIT/OFFSET` sobre `v_movimiento`).
- `FirebaseAuthAdapter`: verifica el token y devuelve el `uid`.
- Pendiente: `SecretsProvider` (Google Cloud Secret Manager) y Cloud SQL en el perfil `gcp` (ADR-0014). Hoy el perfil `gcp` solo pone el prefijo `/api` y los orígenes de CORS (`JJSKAFFEE_CORS_ORIGENES`).

**Base de datos**: PostgreSQL en Cloud SQL con RLS forzado en toda tabla con datos de cafetería. Scripts en `db/` (01–10), en orden: migraciones y seeds de prueba por separado. `usuarios` guarda membresías (UNIQUE `(id_cafeteria, firebase_uid)`); `v_movimiento` une ventas confirmadas, ingresos libres y egresos no anulados, con `security_invoker`.

## Recorrido: registrar un ingreso
1. El frontend obtiene el token de Firebase (login).
2. `GET /api/acceso/cafeterias` (solo token): la persona ve sus cafeterías con su rol en cada una y elige una.
3. `GET /api/acceso/yo` con `X-Cafeteria-Id`: el frontend arma el menú con los permisos.
4. `GET /api/ingresos/catalogo`: la pantalla de venta muestra productos, adiciones, precios y (al administrador) las categorías de ingreso.
5. `POST /api/ingresos` (venta) o `POST /api/ingresos/libres` (ingreso libre, solo administrador). El API Gateway valida el token de Firebase y enruta a Cloud Run con el token original en `X-Forwarded-Authorization`.
6. `FirebaseAuthFilter` vuelve a verificar el token y arma `UsuarioAutenticado` con el rol de esa cafetería.
7. El controller pasa el request, la `Idempotency-Key` y el usuario al puerto de entrada.
8. El caso de uso, dentro de `UnidadDeTrabajo.ejecutar(idCafeteria, ...)`, valida, crea la entidad y llama al puerto de salida.
9. El repositorio hace el `INSERT` con JDBC; PostgreSQL lo guarda con RLS de la cafetería.
10. `GET /api/movimientos`: el ingreso aparece como INGRESO, con los totales del filtro.

> Nota: el diagrama dibuja "Comandos" (`RegistrarIngresoComando`, `RegistrarEgresoComando`) entre el DTO y el caso de uso. El código **no** usa comandos: los casos de uso reciben los DTO request. Antes de agregarlos, preguntar a Joel si quiere alinear el código con el diagrama o el diagrama con el código.

## Registrar ingreso
Un ingreso es una **venta** de productos del menú (`POST /ingresos`, tabla `venta` con `detalle_venta`) o un **ingreso libre** (`POST /ingresos/libres`, tabla `ingreso_libre`): monto + categoría de ingreso, solo para el administrador. Ninguna validación está en el controller: este solo pasa el cuerpo, el encabezado `Idempotency-Key` y el usuario.

### Venta (`RegistrarIngreso`)
| # | Regla | Dónde | Respuesta |
|---|---|---|---|
| 1 | Token válido, `X-Cafeteria-Id` válido y membresía activa en esa cafetería; cafetería, usuario y fecha salen del servidor (nunca del cuerpo) | `FirebaseAuthFilter`, `AutenticarUsuario`, `Clock` | 401 / 400 / 403 |
| 2 | La cafetería está **activa** (`cafeteria.activo`; si el RLS no la deja ver, cuenta como inactiva) | `RegistrarIngreso` + `CafeteriaRepository` | 422 |
| 3 | **Idempotencia**: con `Idempotency-Key` repetida en la misma cafetería y el mismo contenido se devuelve la venta ya registrada (201, mismo `idVenta`, aunque esté anulada) | `VentaRepository.buscarPorClaveIdempotencia` | 201 |
| 4 | La misma clave con **otro contenido** (otro `hash_solicitud`) se rechaza | `Venta.verificarMismaSolicitud` | 422 |
| 5 | La clave no es un UUID | Spring (conversión del encabezado) | 400 |
| 6 | Al menos un producto | `IngresoRequest` y `Venta.validarParaRegistrar` | 400 / 422 |
| 7 | Cada producto existe en el catálogo de la cafetería (RLS) | `RegistrarIngreso` | 404 |
| 8 | El producto base es `BASE` y está disponible; cada adición es `ADICION`, está disponible y está permitida para ese base (`producto_adicion`) | `Venta.agregar` | 422 |
| 9 | **Precio > 0** y con máximo 2 decimales, en el producto base y en cada adición | `Venta.agregar` | 422 |
| 10 | **Cantidad entera de 1 a 99** en el base y en cada adición (las adiciones repetidas se suman). `1.5` no se trunca: 400 (`spring.jackson.deserialization.accept-float-as-int=false`) | `ItemRequest`/`AdicionRequest` y `Venta.agregar` | 400 / 422 |
| 11 | **Máximo 50 líneas** por venta (cada base y cada adición distinta es una línea) | `Venta.agregar` (`MAXIMO_LINEAS`) | 400 / 422 |
| 12 | **Total > 0, máximo 2 decimales, sin redondeo**: Σ precio × cantidad en `BigDecimal` exacto | `Venta.validarParaRegistrar` | 422 |
| 13 | Venta y detalles se guardan **todo o nada**, con RLS | `UnidadDeTrabajo` | — |

### Ingreso libre (`RegistrarIngresoLibre`)
| # | Regla | Dónde | Respuesta |
|---|---|---|---|
| 1 | Igual que la venta (token, cafetería, membresía) | `FirebaseAuthFilter` | 401 / 400 / 403 |
| 2 | Solo con el permiso `REGISTRAR_INGRESO_LIBRE` (administrador); se revisa antes de tocar la BD | `RegistrarIngresoLibre` e `IngresoLibre.registrar` | 403 |
| 3 | Cafetería activa | `CafeteriaRepository` | 422 |
| 4 | Idempotencia igual que la venta (huella de categoría, monto, descripción y comprobante; 1000 y 1000.00 son lo mismo) | `IngresoLibreRepository` e `IngresoLibre.verificarMismaSolicitud` | 201 / 422 |
| 5 | La categoría existe en la cafetería (RLS) | `RegistrarIngresoLibre` | 404 |
| 6 | Categoría de tipo INGRESO y no archivada | `IngresoLibre.registrar` | 422 |
| 7 | Monto > 0, máximo 2 decimales y 10 dígitos enteros (`numeric(12,2)`), **sin redondear** | `@Digits(integer = 10, fraction = 2)` en el DTO e `IngresoLibre.registrar` | 400 / 422 |
| 8 | Descripción obligatoria (máx. 200); comprobante opcional (máx. 50) | DTO e `IngresoLibre.registrar` | 400 / 422 |

### Idempotencia
- Columnas `clave_idempotencia` (uuid) y `hash_solicitud` (SHA-256 en hexadecimal, `HuellaDeSolicitud`) en `venta` (`db/05`) e `ingreso_libre` (`db/09`), con `UNIQUE (id_cafeteria, clave_idempotencia)` y un `CHECK` que exige las dos juntas. Sin encabezado, ambas quedan `NULL` y cada petición es un registro nuevo.
- Dos peticiones iguales al mismo tiempo: el `INSERT ... ON CONFLICT DO NOTHING` de la segunda no inserta, `guardar` devuelve `false` y el caso de uso responde con el registro que ganó.
- La misma clave en otra cafetería es otro registro (la UNIQUE y el RLS son por cafetería).

### Fechas
`fecha_hora` y `cafeteria.creada_en` son `timestamptz`; el `Clock` del servidor es UTC y `ConversionesSql` escribe `OffsetDateTime` en UTC. La hora de Colombia solo se usa para interpretar los filtros `desde`/`hasta`.

### Permisos de `app_jjskaffee` en la BD
- `cafeteria`: `SELECT (id, activo)` (`db/05`) y `SELECT (nombre)` (`db/07`). Nada más.
- `venta`, `detalle_venta`, `egreso`, `ingreso_libre`: `SELECT, INSERT`; de `venta` además `UPDATE (estado, motivo_anulacion)`.
- Catálogo y `usuarios`: solo `SELECT`. Nadie tiene `DELETE`.
- Se descartó una función `SECURITY DEFINER` para leer la cafetería: correría como `postgres` y se saltaría el RLS.

## Otras reglas de negocio en el dominio
- **Deshacer venta**: solo quien la registró, dentro de 60 s, si no estaba anulada; después la anula el administrador.
- **Egreso**: solo el administrador; monto > 0; categoría de egreso y activa; descripción obligatoria (máx. 200); comprobante opcional (máx. 50).
- **Consulta de movimientos** (`FiltroMovimientos`): `desde` ≤ `hasta` (hora de Colombia, `hasta` incluye el día completo); página ≥ 0 y tamaño 1–50 (por defecto 20); filtros por tipo y categoría. Con `VER_TODOS_LOS_MOVIMIENTOS` (administrador) se ve todo; el cajero solo lo que él registró. Las ventas y los ingresos libres salen como INGRESO (los libres con su categoría); los totales (ingresos, egresos, balance) se calculan con SQL sobre todo el filtro.
- **Catálogo de venta** (`ProductoDelMenu`): solo productos base disponibles con precio > 0 y sus adiciones permitidas disponibles con precio > 0, es decir, lo que `Venta` aceptaría.
- Patrones: entidades con fábrica (`crear` / `registrar`) que valida y `reconstruir` para el repositorio; IDs `UUID`; dinero en `BigDecimal`; tiempo con `Instant` y `Clock` inyectado.

## Pruebas
- Unitarias (`./mvnw test`): dominio (`VentaTest`, `IngresoLibreTest`, `ProductoDelMenuTest`, `EgresoTest`, `FiltroMovimientosTest`, `RolTest`), casos de uso con los fakes de `RepositoriosEnMemoria` (`RegistrarIngresoTest`, `RegistrarIngresoLibreTest`, `ConsultarCatalogoDeVentaTest`, `RegistrarEgresoTest`, `ConsultarMovimientosTest`, `AutenticarUsuarioTest`, `ConsultarAccesoTest`), `FirebaseAuthFilterTest` (incluye `X-Forwarded-Authorization` y el context-path `/api`), `FirebaseAuthAdapterTest`, `ArquitecturaTest` y `ContratoApiGatewayTest` (yaml del gateway ↔ controllers).
- Integración contra PostgreSQL (`./mvnw verify`, paquete `integracion`): `AislamientoEntreCafeteriasIT`, `MultiCafeteriaIT`, `AccesoControllerIT`, `IngresosYEgresosPostgresIT`, `RegistrarIngresoValidacionesIT`, `IngresosLibresIT`, `CatalogoDeVentaIT`, `TransaccionTodoONadaIT`, `MovimientosControllerIT` y `FlujoRegistrarIngresoIT` (el flujo completo por HTTP). Usan un reloj fijo en 2099 y deshacen sus cambios. `ConfiguracionDePrueba` traduce cada token de prueba a un `uid`; la membresía sale de los seeds reales.

## Calidad en CI
GitHub Actions (`.github/workflows/seguridad.yml`): crea la base con los scripts `db/01`…`db/10` en orden, corre `./mvnw verify` (unitarias, ArchUnit, contrato del API Gateway e integración) y escanea con **Trivy** (el `pom.xml` fuerza `tomcat.version`, `jackson-bom.version` y `guava.version` por CVEs reportados; Guava, con una entrada en `dependencyManagement` porque Spring Boot no la maneja).
