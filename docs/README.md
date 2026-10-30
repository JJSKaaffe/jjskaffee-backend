# Documentación de JJSKaffee

Exportada del Project de Claude "ISW2_Joel" el 12 de octubre de 2026 para que Claude Code tenga el mismo contexto. Si una decisión cambia en el Project, actualiza también estos archivos.

| Carpeta / archivo | Contenido | Fuente original |
|---|---|---|
| `negocio/negocio-y-alcance.md` | Qué es el producto, diferenciales, roles, monetización, **decisiones vigentes**, fuera de alcance, contexto académico | negocio cafeteria.docx + decisiones del Project |
| `negocio/vision-y-mapa-de-impacto.md` | Visión y mapa de impacto (objetivo, actores, impactos, entregables) | Mapa de Impacto y vision.xlsx |
| `requisitos/requisitos-funcionales.md` | RF1–RF63 y clasificación valor/reto | negocio cafeteria.docx, Tarea pre-drivers.docx |
| `requisitos/historias-y-funcionalidades-significativas.md` | HU01–HU13 y funcionalidades significativas con observaciones | FuncionalidadesSignificativas.xlsx |
| `restricciones/restricciones-de-negocio.md` | Restricciones humanas, legales, de tiempo, presupuesto y proceso con plan de acción | Restricciones de negocio.xlsx |
| `restricciones/restricciones-tecnicas.md` | Restricciones técnicas propias del proyecto | RestriccionesTécnicas.xlsx |
| `arquitectura/backend-monolito-modular.md` | Monolito modular: módulos, capas, reglas ArchUnit, endpoints, entrada pública (WAF y API Gateway), reglas de negocio del código | Diagrama hexagonal + código |
| `arquitectura/img/hexagonal-ingresos-egresos.png` | Diagrama de arquitectura hexagonal | Hexagonal ingresos y egresos.png |
| `arquitectura/adr/README.md` | ADR-0001…0018 y SPIKE-01…06 resumidos (texto completo en `doc/adr/` y `doc/spikes/`) | adr-*.md, spike-* |
| `despliegue/api-gateway.md` | Paso a paso para montar el API Gateway delante de Cloud Run privado (PowerShell + gcloud) | ADR-0017 + este repo |
| `despliegue/waf.md` | Montar, revisar, activar y quitar el WAF (balanceador + Cloud Armor) delante del API Gateway | ADR-0018 + este repo |
| `postman/jjskaffee-registrar-ingreso.postman_collection.json` | Colección de Postman con el flujo login → elegir cafetería → rol → registrar ingreso → movimientos | Este repo |
| `calidad/atributos-y-escenarios.md` | Prioridad de atributos, escenarios, tácticas, catálogo | Escenarios de calidad.docx, Tarea pre-drivers.docx |
| `calidad/priorizacion-y-escenarios-qa.md` | Priorización por rol y escenarios QA (fuente/estímulo/…/medida) | tradeoff final version.xlsx |
| `calidad/dot-voting.md` | Dot voting por atributo y rol | Dot voting.xlsx |
| `calidad/matriz-de-tiempos.md` | Tiempos máximos por transacción | Matriz de Tiempos del Sistema.xlsx |
| `calidad/mapa-empatia.png` | Mapa de empatía (radar de atributos por rol) | Mapa-empatia-sw2.png |
