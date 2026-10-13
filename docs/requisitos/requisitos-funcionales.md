# Requisitos funcionales (RF1–RF63)

> Fuente: "negocio cafeteria.docx" (Project ISW2_Joel). Lista completa tal como está documentada.
> Los RF que implementa el módulo de **ingresos y egresos** están marcados con ★.

## Registro y login
- **RF1**: El administrador se registra con nombre, apellido, nombre de usuario y contraseña.
- **RF2**: El administrador registra su cafetería (nombre, dirección) al crear su cuenta.
- **RF3**: El administrador registra a un cajero con nombre, apellido, nombre de usuario, rol y contraseña.
- **RF4**: Los nombres de usuario no pueden repetirse.
- **RF5**: Los usuarios acceden con nombre de usuario y contraseña.
- **RF6**: El sistema valida que las credenciales sean correctas.

## Multi-tenant (aislamiento por cafetería)
- **RF7**: Cada usuario (administrador y cajero) está asociado a una única cafetería.
- **RF8**: El administrador solo consulta y gestiona información de su propia cafetería.
- **RF9** ★: El cajero solo registra y consulta movimientos de la cafetería a la que pertenece.
- **RF10**: El administrador edita la información de su cafetería (nombre, dirección).

## Rol: Administrador
- **RF11**: Consultar los cajeros registrados.
- **RF12**: Editar la información de los cajeros.
- **RF13**: Eliminar cajeros registrados.
- **RF14** ★: Registrar egresos de la cafetería.
- **RF15** ★: Consultar, editar y eliminar los egresos registrados.
- **RF16** ★: Consultar los ingresos registrados por los cajeros.
- **RF17** ★: Ver el total de ingresos y egresos.
- **RF18** ★: Crear, editar y eliminar categorías de ingresos y egresos.
- **RF19**: Configurar umbrales de alerta (porcentaje de variación y tipo de comparación: semana/mes/año).
- **RF20**: Consultar las alertas generadas.
- **RF21**: Cerrar sesión.

## Gestión de menú y productos
- **RF22**: Crear, editar y eliminar productos del menú, incluyendo su precio.
- **RF23**: Marcar un producto como "producto base" o "adición".

## Rol: Cajero
- **RF24** ★: Seleccionar un producto base del menú al registrar una venta.
- **RF25** ★: Agregar una o más adiciones a un producto base al registrar una venta.
- **RF26** ★: Calcular automáticamente el total de la venta (producto base + adiciones).
- **RF27** ★: Registrar el detalle de cada venta (producto base y adiciones).
- **RF28** ★: El cajero consulta, edita y elimina los ingresos que él mismo registró.
- **RF29**: El cajero inicia sesión.
- **RF30**: El cajero cierra sesión.

## Dashboard
- **RF31**: Dashboard con métricas del período (día/semana/mes/año) calculadas al momento de la consulta.
- **RF32**: Seleccionar el período a visualizar.

## Reportes y análisis financiero
- **RF33**: Reportes financieros semanales, mensuales y anuales.
- **RF34**: Cálculo automático de la utilidad neta.
- **RF35**: Historial de reportes financieros.
- **RF36**: Comparación automática del período actual vs. el anterior según configuración del administrador.
- **RF37**: Alertas automáticas cuando una variación supera el umbral configurado.
- **RF38** ★: Historial de movimientos filtrado por fecha, categoría o tipo (ingreso/egreso).

## Seguridad y validaciones
- **RF39**: Encriptar las contraseñas de los usuarios.
- **RF40** ★: Restringir el acceso a funcionalidades según el rol.
- **RF41** ★: Validar que todos los campos obligatorios estén completos antes de registrar.
- **RF42** ★: Mostrar mensajes de error ante operaciones inválidas.
- **RF43** ★: Registrar la fecha de los ingresos y egresos.
- **RF44**: Mantener activa la sesión solo para usuarios autenticados.

## Inventario
- **RF45**: Registrar insumos (nombre, unidad de medida, stock actual, stock mínimo).
- **RF46**: Editar un insumo.
- **RF47**: Eliminar un insumo.
- **RF48**: Administrador o cajero registran entradas de inventario (compras).
- **RF49**: Administrador o cajero registran salidas de inventario (consumo o pérdida).
- **RF50**: Actualizar automáticamente el stock al registrar entradas o salidas.
- **RF51**: Alerta cuando el stock actual es menor o igual al stock mínimo.
- **RF52**: Consultar el historial de movimientos de inventario.
- **RF53**: Consultar el inventario actual de la cafetería.

## IA
- **RF54**: Generar recomendaciones con IA a partir de los reportes financieros.
- **RF55**: Presentar las recomendaciones en lenguaje natural.
- **RF56**: Consultar el historial de recomendaciones de IA.
- **RF57**: Notificar alertas en tiempo real generadas por la IA.

## Nómina
- **RF58**: Registrar el salario asignado a cada cajero.
- **RF59**: Editar el salario de un cajero.
- **RF60**: Calcular el valor a pagar por cajero según el período de nómina (quincenal, mensual).
- **RF61**: Registrar el pago de nómina como un egreso.
- **RF62**: Consultar el historial de pagos de nómina.
- **RF63**: Alerta cuando el pago de nómina esté próximo a su fecha límite.

## Clasificación valor de negocio / reto técnico (resumen)
Fuente: "Tarea pre-drivers.docx". Diferenciales de negocio: RF18 (categorías personalizadas), RF19 (umbrales), RF31 (dashboard), RF36 (comparación configurable), RF37 (alertas), RF51 (alerta de stock). Retos técnicos: RF4, RF6, RF7, RF21, RF23, RF26, RF27, RF38, RF39–RF44, RF50. Ambos: RF8, RF9, RF24–RF25.
