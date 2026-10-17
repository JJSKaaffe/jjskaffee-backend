-- =====================================================================
-- JJSKaffee · 04 · Seed de prueba: usuarios (administrador y cajeros)
-- Ejecutar en pgAdmin conectado a la base "jjskaffee" con el usuario postgres,
-- después de 03_seed_cafeterias_y_catalogo.sql (necesita las cafeterías A y B).
--
-- Solo para desarrollo y pruebas: en producción la tabla usuarios la llena el
-- módulo de autenticación. Mismos UUID que src/test/java/.../DatosPrueba.java.
--
-- Para probar la API con tu cuenta real de Firebase, cambia el firebase_uid
-- de un usuario por tu uid (Firebase Console > Authentication > Users):
--   UPDATE usuarios SET firebase_uid = '<tu uid>' WHERE id = 'a0000000-0000-0000-0000-000000000001';
-- y pon en tu token el custom claim id_cafeteria de esa misma cafetería.
-- =====================================================================

BEGIN;

INSERT INTO usuarios (id, id_cafeteria, firebase_uid, nombre, rol) VALUES
    -- Cafetería A
    ('a0000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'uid-prueba-laura',  'Laura Gómez', 'ADMINISTRADOR'),
    ('a0000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'uid-prueba-andres', 'Andrés Ruiz', 'CAJERO'),
    -- Cafetería B
    ('a0000000-0000-0000-0000-000000000003', '22222222-2222-2222-2222-222222222222', 'uid-prueba-marta',  'Marta Díaz',  'ADMINISTRADOR'),
    ('a0000000-0000-0000-0000-000000000004', '22222222-2222-2222-2222-222222222222', 'uid-prueba-camilo', 'Camilo Peña', 'CAJERO');

COMMIT;
