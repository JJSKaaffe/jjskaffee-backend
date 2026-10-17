-- =====================================================================
-- JJSKaffee · 03 · Seed de prueba: cafeterías y catálogo (A y B)
-- Ejecutar en pgAdmin conectado a la base "jjskaffee" con el usuario postgres,
-- después de 02_esquema.sql. Los usuarios van aparte en 04_seed_usuarios.sql.
--
-- Son los mismos UUID de src/test/java/.../DatosPrueba.java: las pruebas de
-- integración dependen de ellos. No hay ventas ni egresos: las pruebas crean
-- los suyos y los deshacen al terminar (rollback).
-- =====================================================================

BEGIN;

INSERT INTO cafeteria (id, nombre) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Café La Esquina (A)'),
    ('22222222-2222-2222-2222-222222222222', 'Café El Parque (B)');

-- ---------------------------------------------------------------------
-- Cafetería A
-- ---------------------------------------------------------------------
INSERT INTO categoria (id, id_cafeteria, nombre, tipo, archivada) VALUES
    ('c0000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Bebidas calientes',       'MENU',   false),
    ('c0000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Panadería',               'MENU',   false),
    ('c0000000-0000-0000-0000-000000000011', '11111111-1111-1111-1111-111111111111', 'Insumos',                 'EGRESO', false),
    ('c0000000-0000-0000-0000-000000000012', '11111111-1111-1111-1111-111111111111', 'Servicios públicos',      'EGRESO', false),
    ('c0000000-0000-0000-0000-000000000015', '11111111-1111-1111-1111-111111111111', 'Mantenimiento (antigua)', 'EGRESO', true);

INSERT INTO producto (id, id_cafeteria, id_categoria, nombre, precio, tipo, disponible) VALUES
    ('b0000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'c0000000-0000-0000-0000-000000000001', 'Espresso',               4000, 'BASE',    true),
    ('b0000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'c0000000-0000-0000-0000-000000000001', 'Café latte',             6500, 'BASE',    true),
    ('b0000000-0000-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111', 'c0000000-0000-0000-0000-000000000002', 'Pandebono',              2500, 'BASE',    true),
    ('b0000000-0000-0000-0000-000000000011', '11111111-1111-1111-1111-111111111111', 'c0000000-0000-0000-0000-000000000001', 'Leche de almendra',      1500, 'ADICION', true),
    ('b0000000-0000-0000-0000-000000000012', '11111111-1111-1111-1111-111111111111', 'c0000000-0000-0000-0000-000000000001', 'Shot extra de espresso', 2000, 'ADICION', true);

INSERT INTO producto_adicion (id_cafeteria, id_producto_base, id_adicion) VALUES
    ('11111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000011'),
    ('11111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000012'),
    ('11111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000012');

-- ---------------------------------------------------------------------
-- Cafetería B
-- ---------------------------------------------------------------------
INSERT INTO categoria (id, id_cafeteria, nombre, tipo, archivada) VALUES
    ('c0000000-0000-0000-0000-000000000021', '22222222-2222-2222-2222-222222222222', 'Bebidas',  'MENU',   false),
    ('c0000000-0000-0000-0000-000000000031', '22222222-2222-2222-2222-222222222222', 'Insumos',  'EGRESO', false);

INSERT INTO producto (id, id_cafeteria, id_categoria, nombre, precio, tipo, disponible) VALUES
    ('b0000000-0000-0000-0000-000000000021', '22222222-2222-2222-2222-222222222222', 'c0000000-0000-0000-0000-000000000021', 'Capuchino', 7000, 'BASE', true);

COMMIT;
