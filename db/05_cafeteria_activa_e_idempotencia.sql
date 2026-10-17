-- =====================================================================
-- JJSKaffee · 05 · Cafetería activa e idempotencia del registro de ingresos
-- Ejecutar en pgAdmin conectado a la base "jjskaffee" con el usuario postgres,
-- después de 02_esquema.sql (y antes del seed 06_seed_cafeteria_inactiva.sql).
--
-- Migración, no seed: no inserta datos. Se corre una sola vez por base.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- Cafetería activa
-- Solo una cafetería activa puede registrar ingresos. Las existentes quedan
-- activas (DEFAULT true).
-- ---------------------------------------------------------------------
ALTER TABLE cafeteria ADD COLUMN activo boolean NOT NULL DEFAULT true;

-- CAMBIO respecto a 02_esquema.sql: allí la tabla cafeteria "no se le da a la
-- aplicación". Ahora la app necesita saber si la cafetería del usuario está
-- activa antes de registrar un ingreso, y eso hace parte de que cada usuario
-- opere solo dentro de su cafetería (RF7–RF9).
--
-- Se da lo mínimo: SELECT de las columnas id y activo (nada de nombre ni de
-- INSERT/UPDATE). La tabla ya tiene RLS forzado con la política
-- "id = app_cafeteria_actual()", así que la app solo puede leer la fila de la
-- cafetería fijada en la transacción, nunca la de otra.
--
-- Se descartó una función SECURITY DEFINER (p. ej. app_cafeteria_activa()):
-- correría con los permisos de su dueño, postgres, que es superusuario y se
-- salta el RLS. Un error en esa función (o un id mal pasado) dejaría consultar
-- cualquier cafetería, justo lo que SPIKE-03 y ADR-0012 prohíben. Con el GRANT
-- por columnas la protección sigue siendo el RLS de siempre.
GRANT SELECT (id, activo) ON cafeteria TO app_jjskaffee;

-- ---------------------------------------------------------------------
-- Idempotencia de POST /ingresos (encabezado Idempotency-Key)
-- Si el frontend reintenta la misma venta (doble clic, red lenta) con la misma
-- clave, el backend devuelve la venta ya registrada en vez de crear otra.
--   clave_idempotencia: el UUID del encabezado (NULL si no se mandó).
--   hash_solicitud: SHA-256 en hexadecimal de los productos, cantidades y
--   adiciones pedidos; con la misma clave y otro contenido se responde 422.
-- La restricción UNIQUE es por cafetería: la misma clave en otra cafetería es
-- otra venta. Varias ventas sin clave (NULL) no chocan entre sí.
-- La app ya tiene INSERT sobre venta, que cubre las columnas nuevas, y RLS
-- forzado, así que la búsqueda por clave solo ve las ventas de su cafetería.
-- ---------------------------------------------------------------------
ALTER TABLE venta ADD COLUMN clave_idempotencia uuid;
ALTER TABLE venta ADD COLUMN hash_solicitud char(64);
ALTER TABLE venta ADD CONSTRAINT uq_venta_cafeteria_clave_idempotencia
    UNIQUE (id_cafeteria, clave_idempotencia);
ALTER TABLE venta ADD CONSTRAINT ck_venta_clave_con_hash
    CHECK ((clave_idempotencia IS NULL) = (hash_solicitud IS NULL));

COMMIT;
