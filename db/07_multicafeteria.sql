-- =====================================================================
-- JJSKaffee · 07 · Multi-cafetería por persona (membresías) (ADR-0016)
-- Ejecutar en pgAdmin conectado a la base "jjskaffee" con el usuario postgres,
-- después de 06_seed_cafeteria_inactiva.sql (y antes del seed 08_seed_multicafeteria.sql).
--
-- Migración, no seed: no inserta datos. Se corre una sola vez por base.
--
-- Qué cambia:
--   * Una persona (firebase_uid) puede pertenecer a varias cafeterías, con un rol
--     distinto en cada una: la tabla usuarios pasa a guardar MEMBRESÍAS, una fila
--     por persona y cafetería. Las FK de venta y egreso no cambian: siguen
--     apuntando a (id_cafeteria, id) de la membresía.
--   * El token de Firebase ya no trae la cafetería (custom claim id_cafeteria del
--     ADR-0001): el frontend la elige con el encabezado X-Cafeteria-Id y el backend
--     comprueba la membresía con RLS de esa cafetería, como siempre.
--   * Para la pantalla "elige tu cafetería" (GET /acceso/cafeterias) hace falta
--     consultar SIN cafetería fijada. Para eso hay una segunda variable de sesión,
--     app.current_uid, que solo deja ver las membresías de esa persona y las
--     cafeterías donde tiene una activa. La aplicación nunca fija las dos
--     variables a la vez (PgUnidadDeTrabajo borra una al fijar la otra).
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- Membresías: un mismo uid puede repetirse, pero una sola vez por cafetería.
-- El índice por firebase_uid reemplaza al que daba la UNIQUE anterior y sirve
-- para listar las cafeterías de una persona.
-- ---------------------------------------------------------------------
ALTER TABLE usuarios DROP CONSTRAINT usuarios_firebase_uid_key;
ALTER TABLE usuarios ADD CONSTRAINT uq_usuarios_cafeteria_firebase_uid UNIQUE (id_cafeteria, firebase_uid);
CREATE INDEX ix_usuarios_firebase_uid ON usuarios (firebase_uid);

-- ---------------------------------------------------------------------
-- Identidad de la transacción (uid de Firebase).
-- PgUnidadDeTrabajo.ejecutarConIdentidad la fija con
-- set_config('app.current_uid', uid, true): dura hasta el fin de la transacción.
-- Si no está fijada devuelve NULL y las políticas de abajo no dejan ver nada.
-- ---------------------------------------------------------------------
CREATE FUNCTION app_uid_actual() RETURNS varchar
    LANGUAGE sql STABLE
AS $$
    SELECT NULLIF(current_setting('app.current_uid', true), '')
$$;

-- ---------------------------------------------------------------------
-- Políticas de solo lectura con la identidad. Se suman (OR) a la política
-- aislamiento_cafeteria de 02_esquema.sql, que sigue siendo la única que deja
-- escribir. Con app.current_uid fijada y sin cafetería:
--   * usuarios: solo las filas (membresías) de esa persona, de cualquier cafetería.
--   * cafeteria: solo las cafeterías donde esa persona tiene una membresía activa.
-- Las demás tablas (ventas, egresos, catálogo...) no tienen política por uid:
-- sin cafetería fijada no muestran nada.
-- ---------------------------------------------------------------------
CREATE POLICY membresias_propias ON usuarios
    FOR SELECT
    USING (firebase_uid = app_uid_actual());

CREATE POLICY cafeterias_del_usuario ON cafeteria
    FOR SELECT
    USING (EXISTS (SELECT 1
                   FROM usuarios u
                   WHERE u.id_cafeteria = cafeteria.id
                     AND u.firebase_uid = app_uid_actual()
                     AND u.activo));

-- ---------------------------------------------------------------------
-- Permisos: la lista de cafeterías muestra su nombre. Se suma a
-- SELECT (id, activo) de 05_cafeteria_activa_e_idempotencia.sql; sigue sin
-- haber INSERT, UPDATE ni DELETE sobre cafeteria, ni acceso a creada_en.
-- ---------------------------------------------------------------------
GRANT SELECT (nombre) ON cafeteria TO app_jjskaffee;

COMMIT;
