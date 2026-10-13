# Negocio, alcance y decisiones de producto

> Fuentes: "negocio cafeteria.docx" (guion de resumen ejecutivo), mapa de impacto y decisiones tomadas en las conversaciones del Project ISW2_Joel.
> Varias decisiones cambiaron durante el semestre. La sección **Decisiones vigentes** manda sobre cualquier texto antiguo.

## Qué es JJSKaffee
JJSKaffee es una desarrolladora de nicho para pequeños emprendedores. El producto es un sistema web SaaS para que cafeterías pequeñas e independientes (público objetivo nacional, Colombia) administren sus ingresos y egresos, con reportes, comparaciones entre períodos y alertas configurables, para mejorar la toma de decisiones y el control del negocio.

**Diferenciales frente a la competencia** (Siigo, Vendty, Loggro y sobre todo **Treinta**, el competidor directo):
- Especializado en cafeterías, no horizontal.
- Personalización por cafetería: categorías, menú, umbrales de alerta.
- Comparación configurable del período actual vs. el anterior (semana/mes/año).
- Motor de alertas basado en tendencias financieras (Treinta solo alerta inventario).
- IA que explica en lenguaje natural qué pasa y qué hacer.

**Roles**: Administrador (dueño) y Cajero. No hay otros perfiles ni niveles entre administradores en esta versión.

**Modelo de negocio**: plan básico gratis con anuncios; funciones premium (p. ej. IA, contabilidad específica) por créditos o suscripción. El cobro de la suscripción se gestiona por fuera del sistema (sin pasarela).

## Decisiones vigentes (las más recientes ganan)
| Tema | Decisión vigente | Antes se dijo |
|---|---|---|
| Tenencia | **Multi-tenant**: una base de datos compartida por varias cafeterías, aislada por `id_cafeteria` + RLS | Una BD por cafetería; "un único establecimiento" |
| IA | **Sí hay IA** para recomendaciones y explicación de alertas; las alertas se calculan con reglas configurables y la IA explica | "No se implementarán modelos de IA" |
| Nómina | **Sí**, básica (salario × período), sin prestaciones ni seguridad social | "No contempla nómina" |
| Inventario | **Sí** se incluye (cantidad, nombre, precio, distribuidora / stock mínimo) | "En veremos" |
| Dashboard | Se calcula **al consultar**, no en tiempo real | Visión decía "tiempo real" |
| Alertas | Notificación en 5–10 s tras el evento (FCM) | — |
| Pagos | **Sin pasarela de pagos**; ingresos y egresos se registran manualmente | — |
| Reportes | Diarios, semanales, mensuales y anuales, con comparación configurable y recomendaciones | Solo semanal/mensual/anual |
| Arquitectura | Monolito sin estado desplegado en GCP; el backend de ingresos/egresos con **arquitectura hexagonal** (el profesor dijo que el monolito modular era muy complicado) | MVC / monolito modular |
| Repos | Dos repos separados (frontend y backend) en la organización GitHub **JJSKaaffe** | Monorepo "JJSkafee" |
| Frontend | **Vue 3** | — |
| Building blocks | Internos: Egresos, Ingresos, Inventario, Nómina. Externos vía API: Seguridad, Gestión de usuarios, Alertas, IA | — |

## Mapa de impacto (resumen)
Objetivo: que las cafeterías administren ingresos y egresos de forma eficiente para mejorar decisiones y control.
- **Administrador**: gestionar egresos (registro, control, clasificación por categoría); supervisar ingresos (historial de ventas, consulta por período o categoría); personalizar alertas (CRUD de umbrales, consultar alertas); analizar información financiera (balance general, reportes); gestionar menú y precios.
- **Cajero**: gestionar ingresos (registrar ventas seleccionando productos del menú; consultar, editar y eliminar ingreso).

Detalle completo en `vision-y-mapa-de-impacto.md`.

## Fuera de alcance (MVP)
Facturación electrónica DIAN, multi-sucursal, pasarelas de pago, datáfonos, e-commerce/domicilios, ERP completo, contabilidad con validez legal (NIIF). El sistema es de **control interno**: no usar términos como "estados financieros oficiales" y mostrar avisos de que no reemplaza al contador ni exime de facturar.

## Retos identificados
Técnicos: diseño de BD transaccional con trazabilidad; reportes agregados exactos y consistentes; seguridad y roles; validación de datos financieros (sin montos negativos ni duplicados); rendimiento con volumen creciente; integridad sin pérdida de datos; interfaz intuitiva.
Equipo: coordinación, control de alcance, plazos, integración del trabajo sin conflictos, adaptarse a cambios de requisitos del profesor.

## Contexto académico
Proyecto de Ingeniería de Software II (UCO), 5 meses. El profesor separó el grupo original; **la funcionalidad a cargo de Joel es registrar ingresos y egresos**. El profesor pide pensar el proyecto como desarrollo profesional y exige coherencia entre documentación e implementación.

Requisitos del profesor para el C4: incluir un **vault de secretos**; el **API Gateway** debe aparecer también en el nivel 1; en el nivel 2 mostrar la trazabilidad del backend de ingresos y egresos.
