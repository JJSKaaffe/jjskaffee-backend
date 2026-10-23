-- =====================================================================
-- JJSKaffee · 08 · Seed de prueba: una persona en dos cafeterías
-- Ejecutar en pgAdmin conectado a la base "jjskaffee" con el usuario postgres,
-- después de 07_multicafeteria.sql (necesita la UNIQUE (id_cafeteria, firebase_uid)).
--
-- Laura (uid-prueba-laura) ya es ADMINISTRADORA en la cafetería A (04_seed_usuarios.sql);
-- aquí también queda como CAJERA en la cafetería B, con otro id de membresía.
-- Con el mismo token, el rol depende de la cafetería que elija en X-Cafeteria-Id.
-- Mismos UUID que src/test/java/.../DatosPrueba.java.
-- =====================================================================

BEGIN;

INSERT INTO usuarios (id, id_cafeteria, firebase_uid, nombre, rol) VALUES
    ('a0000000-0000-0000-0000-000000000006', '22222222-2222-2222-2222-222222222222', 'uid-prueba-laura', 'Laura Gómez', 'CAJERO');

COMMIT;
