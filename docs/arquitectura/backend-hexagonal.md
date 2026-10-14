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
| | `servicios.output.ports` | Puertos de salida (interfaces): `VentaRepository`, `EgresoRepository`, `CatalogoRepository`, `MovimientoRepository`, `UnidadDeTrabajo`, `VerificadorDeToken` | |
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
  - `POST /ingresos` → 201 `IngresoResponse`
  - `POST /ingresos/{idVenta}/deshacer` → 204
  - `POST /egresos` → 201 `EgresoResponse`
  - `GET /movimientos?desde&hasta&tipo&idCategoria&pagina&tamano` → 200 `MovimientosResponse` (página ≤ 50 y totales de todo el filtro)
- `FirebaseAuthFilter`: exige `Authorization: Bearer <token>`, lo valida con `VerificadorDeToken` y deja `UsuarioAutenticado` en la petición; sin token válido responde 401.
- `ManejadorDeErrores`: ProblemDetail (RFC 9457). 400 datos inválidos (con `errores` por campo), 401, 403 `SinPermisoException`, 404 `RecursoNoEncontradoException`, 422 `ReglaNegocioException`, 500 sin detalles internos.

**Salida**
- `PgVentaRepository`, `PgEgresoRepository`, `PgCatalogoRepository`, `PgMovimientoRepository` (totales con `SUM ... FILTER` y página con `LIMIT/OFFSET` sobre `v_movimiento`): JDBC sobre PostgreSQL.
- `PgUnidadDeTrabajo`: abre la transacción y ejecuta `set_config('app.current_cafeteria', id, true)` para RLS.
- `FirebaseAuthAdapter` (implementa `VerificadorDeToken`): verifica el token, toma `id_cafeteria` del claim y busca `id`, `nombre` y `rol` en la tabla `usuarios` (con RLS de esa cafetería, solo usuarios activos).
- Pendiente: `SecretsProvider` (Google Cloud Secret Manager) con el perfil `gcp`.

**Externos**: PostgreSQL en Cloud SQL (tablas `cafeteria`, `usuarios`, `venta`, `detalle_venta`, `egreso`, `categoria`, `producto`, `producto_adicion` con RLS por cafetería; esquema en `db/02_esquema.sql`, seeds de prueba en `db/03` y `db/04`), Firebase Authentication, Google Cloud Secret Manager.

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

## Reglas de negocio implementadas en el dominio
- **Venta** (ingreso): la pueden registrar cajero y administrador. Total = Σ(producto base + adiciones) calculado **siempre en el servidor con precios del catálogo**; nunca se acepta un total del navegador. El base debe ser `BASE` y estar disponible; cada adición debe ser `ADICION`, disponible y permitida para ese base (`producto_adicion`); cantidades ≥ 1; una venta sin productos no se registra. Adiciones repetidas se suman. **Deshacer**: solo quien la registró, dentro de 60 s, si no estaba anulada; después la anula el administrador.
- **Egreso**: solo el **administrador**; monto > 0; categoría de egreso y activa; descripción obligatoria (máx. 200); comprobante opcional (máx. 50).
- **Consulta de movimientos** (`FiltroMovimientos`): `desde` ≤ `hasta` (fechas en hora de Colombia, `hasta` incluye el día completo); página ≥ 0 y tamaño 1–50 (por defecto 20); filtros por tipo y categoría; el administrador ve todo; el cajero **solo lo que él registró**; devuelve la página y los totales (ingresos, egresos, balance) de todo el filtro, calculados con SQL.
- Patrones: entidades con fábrica (`crear` / `registrar`) que valida, y `reconstruir` para el repositorio; IDs `UUID`; dinero en `BigDecimal`; tiempo con `Instant` y `Clock` inyectado.

## Pruebas existentes
- Unitarias (`./mvnw test`): `dominio/{Venta,Egreso,FiltroMovimientos}Test`, `servicios/casouso/{RegistrarIngreso,RegistrarEgreso,ConsultarMovimientos}Test` con `RepositoriosEnMemoria` (fakes de los puertos), `FirebaseAuthFilterTest`, `FirebaseAuthAdapterTest`, `DatosPrueba` y `ArquitecturaTest`.
- Integración contra PostgreSQL (`./mvnw verify`, paquete `integracion`): `AislamientoEntreCafeteriasIT` (A no ve ni escribe datos de B, rol sin superusuario/BYPASSRLS, RLS forzado en toda tabla, vista con `security_invoker`), `IngresosYEgresosPostgresIT`, `TransaccionTodoONadaIT` y `MovimientosControllerIT` (HTTP completo). Usan un reloj fijo en 2099 y deshacen sus cambios.

## Calidad en CI
GitHub con **Trivy** (vulnerabilidades; el `pom.xml` ya fuerza `tomcat.version` y `jackson-bom.version` por CVEs reportados) y **ArchUnit**.
