# Restricciones técnicas

> Fuente: RestriccionesTécnicas.xlsx (RestriccionesTécnicas1.xlsx es idéntico). Nota: 'arquitectura monolítica' sigue vigente como despliegue; el backend de ingresos/egresos se organiza internamente con arquitectura hexagonal.


## Hoja: RestriccionesTécnicas

| Tipo | Categoria | Restricción técnica | Justificación |
|---|---|---|---|
| Propia del proyecto | Arquitectura | Adoptar una arquitectura monolítica (frontend, lógica de negocio y acceso a datos integrados en una sola aplicación). | Dado el tamaño y la experiencia del equipo, una arquitectura monolítica reduce la complejidad operativa y de despliegue frente a microservicios, permitiendo concentrar el esfuerzo en la calidad funcional del producto. Esto favorece un desarrollo más ágil y manejable en el tiempo disponible, sin sacrificar la posibilidad de evolucionar el sistema en el futuro. |
| Propia del proyecto | Modelo de datos | Diseñar el modelo de datos bajo un enfoque multi-tenant, con aislamiento de información por cafetería. | Al ofrecer el sistema a múltiples cafeterías desde una misma base de datos, el aislamiento de datos por cliente es indispensable para garantizar confidencialidad y escalabilidad comercial. Esta decisión técnica sostiene directamente el modelo de negocio de la plataforma como producto multi-cliente. |
| Propia del proyecto | Prácticas de diseño | Propender por seguir los principios SOLID en el diseño y desarrollo del software. | Aplicar los principios SOLID permite lograr un software más mantenible, escalable y colaborativo. Esto contribuye a un desarrollo más eficiente y ordenado, facilitando que el equipo pueda incorporar nuevas funcionalidades (como las fases futuras del roadmap) sin comprometer la estabilidad del sistema. |
| Propia del proyecto | Marco metodológico | Hacer uso de una metodología ágil (Scrum) para el desarrollo iterativo de la plataforma. | Scrum promueve la entrega incremental, la adaptabilidad y la validación temprana con los stakeholders (equipo, profesor, usuarios), lo cual es especialmente valioso en un proyecto donde el alcance se ha ajustado varias veces conforme avanza el análisis del negocio. |
| Propia del proyecto | Explicabilidad de IA | Diseñar el motor de alertas sobre reglas de negocio configurables, utilizando la IA principalmente para generar la explicación en lenguaje natural. | Basar el cálculo de las alertas en reglas verificables (umbrales configurables) y no exclusivamente en un modelo de IA de caja negra permite mantener la trazabilidad y confiabilidad de las recomendaciones, reduciendo el riesgo de resultados inconsistentes o difíciles de justificar ante el usuario final. |
| Propia del proyecto | Actualización de datos | El dashboard mostrará información actualizada al momento de la consulta, priorizando la simplicidad frente a un procesamiento en tiempo real. | Implementar actualización en tiempo real implica una infraestructura considerablemente más compleja (websockets, procesamiento en streaming). Priorizar la simplicidad en esta primera versión permite concentrar los recursos de desarrollo en los diferenciales centrales del producto (personalización, comparaciones y alertas). |
| Propia del proyecto | Alcance de nómina | Implementar el cálculo de nómina de forma básica (salario por período), sin prestaciones sociales ni aportes a seguridad social en esta versión. | Un módulo de nómina completo implica una complejidad legal y técnica considerable. Acotar su alcance en esta primera versión permite ofrecer valor real al administrador sin comprometer el cronograma del proyecto, dejando claramente documentado qué componentes quedan para una fase posterior. |

## Hoja: Valores

| Tipo |
|---|
| Propia del proyecto |
| Impuesta por el cliente |
