-- =====================================================================
-- JJSKaffee · 10 · Seed de prueba: categorías de INGRESO (cafetería A)
-- Ejecutar en pgAdmin conectado a la base "jjskaffee" con el usuario postgres,
-- después de 09_ingresos_libres.sql.
--
-- Dos categorías de ingreso activas y una archivada (con historial no se borra,
-- se archiva; en ella ya no se puede registrar). Sirven para los ingresos libres.
-- Mismos UUID que src/test/java/.../DatosPrueba.java.
-- =====================================================================

BEGIN;

INSERT INTO categoria (id, id_cafeteria, nombre, tipo, archivada) VALUES
    ('c0000000-0000-0000-0000-000000000051', '11111111-1111-1111-1111-111111111111', 'Eventos privados',         'INGRESO', false),
    ('c0000000-0000-0000-0000-000000000052', '11111111-1111-1111-1111-111111111111', 'Venta de café en grano',   'INGRESO', false),
    ('c0000000-0000-0000-0000-000000000055', '11111111-1111-1111-1111-111111111111', 'Convenios (antigua)',      'INGRESO', true);

COMMIT;
