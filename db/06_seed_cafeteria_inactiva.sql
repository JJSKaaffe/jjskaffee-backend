-- =====================================================================
-- JJSKaffee · 06 · Seed de prueba: cafetería inactiva (C)
-- Ejecutar en pgAdmin conectado a la base "jjskaffee" con el usuario postgres,
-- después de 05_cafeteria_activa_e_idempotencia.sql (necesita la columna activo).
--
-- Sirve para probar que una cafetería inactiva no puede registrar ingresos
-- (la app no puede cambiar cafeteria.activo, por eso va como seed).
-- Mismos UUID que src/test/java/.../DatosPrueba.java.
-- =====================================================================

BEGIN;

INSERT INTO cafeteria (id, nombre, activo) VALUES
    ('33333333-3333-3333-3333-333333333333', 'Café Cerrado (C, inactiva)', false);

INSERT INTO categoria (id, id_cafeteria, nombre, tipo, archivada) VALUES
    ('c0000000-0000-0000-0000-000000000041', '33333333-3333-3333-3333-333333333333', 'Bebidas', 'MENU', false);

INSERT INTO producto (id, id_cafeteria, id_categoria, nombre, precio, tipo, disponible) VALUES
    ('b0000000-0000-0000-0000-000000000031', '33333333-3333-3333-3333-333333333333', 'c0000000-0000-0000-0000-000000000041', 'Tinto', 2000, 'BASE', true);

INSERT INTO usuarios (id, id_cafeteria, firebase_uid, nombre, rol) VALUES
    ('a0000000-0000-0000-0000-000000000005', '33333333-3333-3333-3333-333333333333', 'uid-prueba-sofia', 'Sofía Mora', 'CAJERO');

COMMIT;
