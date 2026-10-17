# jjskaffee-backend

API REST del sistema de gestión financiera **multi-tenant** para cafeterías pequeñas e independientes, desarrollado por **JJSKaffee**.

> Repositorio hermano: [jjskaffee-frontend](https://github.com/JJSKaaffe/jjskaffee-frontend)

## Documentación de arquitectura

| Carpeta | Contenido |
| --- | --- |
| [`doc/drivers`](doc/drivers/drivers.md) | Drivers arquitectónicos: funcionalidades significativas, restricciones técnicas y de negocio, escenarios de atributos de calidad |
| [`doc/adr`](doc/adr) | Registros de decisiones de arquitectura (ADR-0001 a ADR-0014) |
| [`doc/spikes`](doc/spikes) | Spikes técnicos que validan las decisiones (SPIKE-01 a SPIKE-06) |

## Responsabilidades del backend

- Lógica de negocio y acceso a datos (patrón MVC).
- Módulos internos: **Ingresos**, **Egresos**, **Inventario**, **Nómina** y **Reportes**.
- Integración con servicios externos vía API: autenticación, gestión de usuarios, alertas/notificaciones e IA.
- Validaciones del lado del servidor (montos negativos, registros incompletos, duplicados).

## Decisiones de arquitectura (ADR)

| ADR | Decisión | Spike |
| --- | --- | --- |
| [ADR-0001](doc/adr/adr-0001.md) | Autenticación delegada a **Firebase Authentication**; aislamiento por `id_cafeteria` (custom claim + RLS) | [SPIKE-01](doc/spikes/spike-01.md), [SPIKE-03](doc/spikes/spike-03.md) |
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

## Reglas no negociables

1. Toda consulta se filtra por `id_cafeteria` (middleware + RLS en PostgreSQL).
2. La autorización por rol (Administrador / Cajero) se valida en el backend antes de cada acción.
3. El backend es *stateless* y usa un pool de conexiones a la base de datos.
4. Los reportes se calculan siempre desde la tabla de movimientos (única fuente de verdad).
5. El repositorio no contiene secretos: `.env` está en `.gitignore` y solo se versiona un `.env.example` sin valores (ADR-0014).

## Stack

- Base de datos: PostgreSQL (Cloud SQL, GCP)
- Autenticación: Firebase Authentication
- Notificaciones: Firebase Cloud Messaging
- Nube: Google Cloud Platform · Secretos: Google Cloud Secret Manager
- Lenguaje / framework: *por definir* (Node.js propuesto en los ADR)

## Equipo

JJSKaffee — Proyecto de Ingeniería de Software II.
