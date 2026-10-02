# jjskaffee-backend

API REST del sistema de gestión financiera **multi-tenant** para cafeterías pequeñas e independientes, desarrollado por **JJSKaffee**.

> Repositorio hermano: [jjskaffee-frontend](https://github.com/JJSKaaffe/jjskaffee-frontend)

## Responsabilidades del backend

- Lógica de negocio y acceso a datos (patrón MVC).
- Módulos internos: **Ingresos**, **Egresos**, **Inventario**, **Nómina** y **Reportes**.
- Integración con servicios externos vía API: autenticación, gestión de usuarios, alertas/notificaciones e IA.
- Validaciones del lado del servidor (montos negativos, registros incompletos, duplicados).

## Decisiones de arquitectura (ADR)

| ADR | Decisión |
| --- | --- |
| ADR-0001 | Autenticación delegada a **Firebase Authentication**; `id_cafeteria` como custom claim |
| ADR-0002 | Todo movimiento financiero se registra dentro de una **transacción** (todo o nada) |
| ADR-0003 | Categorías como entidad propia por cafetería, con archivado (no borrado) |
| ADR-0007 | `registrado_por_id` y `fecha_hora` se capturan **automáticamente en el backend** |
| ADR-0011 | IA y notificaciones (Firebase Cloud Messaging) vía API; sin pasarela de pagos |
| ADR-0012 | Base de datos **PostgreSQL** con **Row Level Security** para aislar cada cafetería |

## Reglas no negociables

1. Toda consulta se filtra por `id_cafeteria` (middleware + RLS en PostgreSQL).
2. La autorización por rol (Administrador / Cajero) se valida en el backend antes de cada acción.
3. El backend es *stateless* y usa un pool de conexiones a la base de datos.
4. Los reportes se calculan siempre desde la tabla de movimientos (única fuente de verdad).

## Stack

- Base de datos: PostgreSQL
- Autenticación: Firebase Authentication
- Lenguaje / framework: *por definir* (Node.js propuesto en los ADR)

## Estructura

*Pendiente de inicializar cuando se elija el framework.*

## Equipo

JJSKaffee — Proyecto de Ingeniería de Software II.
