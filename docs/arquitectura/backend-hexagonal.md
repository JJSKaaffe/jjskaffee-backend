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
| Infraestructura | `infraestructura.input` | Adaptadores de entrada (controllers, filtros) — **por construir** | Servicios, Dominio, Transversal |
| | `infraestructura.output` | Adaptadores de salida (PostgreSQL, Firebase, secretos) — **por construir** | |
| Transversal | `transversal` | Utilidades comunes (opcional) | — |

## Reglas que verifica ArchUnit (`ArquitecturaTest`) — no romperlas
1. Capas: Infraestructura no es accedida por nadie; Servicios solo por Infraestructura; Dominio solo por Servicios e Infraestructura.
2. El dominio no depende de `org.springframework..`, `jakarta.persistence..` ni `jakarta.servlet..`.
3. Los servicios no dependen de `org.springframework..`, `java.sql..` ni `com.google.firebase..` → **casos de uso en Java puro, sin `@Service`**; se instancian en una clase de configuración (`BeanConfig`) de infraestructura.
4. `infraestructura.input` no depende de `infraestructura.output` ni de `servicios.casouso` → los controllers hablan **solo con los puertos de entrada**.
5. Todo lo que esté en `servicios.input.ports` y `servicios.output.ports` debe ser **interface**.
6. Sin ciclos entre paquetes.
7. **Sin JPA** en ningún lado: persistencia con **JDBC (`JdbcTemplate`)**.

## Adaptadores previstos en el diagrama (pendientes)
**Entrada**
- `MovimientosController` (`@RestController`): `POST /ingresos`, `POST /egresos`, `GET /movimientos` (expuestos por el API Gateway como `/api/ingresos`, `/api/egresos`).
- `FirebaseAuthFilter`: lee el token JWT del header, lo valida con `VerificadorDeToken` y produce `UsuarioAutenticado`.

**Salida**
- `PgVentaRepository`, `PgEgresoRepository`, `PgCatalogoRepository` (y el de movimientos): JDBC sobre PostgreSQL.
- `PgUnidadDeTrabajo`: abre la transacción y ejecuta `set_config` de la cafetería para RLS.
- `FirebaseAuthAdapter` (implementa `VerificadorDeToken`) y `SecretsProvider` (Google Cloud Secret Manager).

**Externos**: PostgreSQL en Cloud SQL (tablas `venta`, `detalle_venta`, `egreso`, `categoria`, `producto`, `producto_adicion`… con RLS por cafetería), Firebase Authentication, Google Cloud Secret Manager.

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
- **Consulta de movimientos**: `desde` ≤ `hasta`; el administrador ve todo; el cajero **solo lo que él registró**; devuelve lista y totales (ingresos, egresos, balance).
- Patrones: entidades con fábrica (`crear` / `registrar`) que valida, y `reconstruir` para el repositorio; IDs `UUID`; dinero en `BigDecimal`; tiempo con `Instant` y `Clock` inyectado.

## Pruebas existentes
`dominio/VentaTest`, `dominio/EgresoTest`, `servicios/casouso/{RegistrarIngreso,RegistrarEgreso,ConsultarMovimientos}Test` con `RepositoriosEnMemoria` (fakes de los puertos), `DatosPrueba` y `ArquitecturaTest`.

## Calidad en CI
GitHub con **Trivy** (vulnerabilidades; el `pom.xml` ya fuerza `tomcat.version` y `jackson-bom.version` por CVEs reportados) y **ArchUnit**.
