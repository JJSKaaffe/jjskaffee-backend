-- =====================================================================
-- JJSKaffee · 01 · Usuario de la aplicación y base de datos
-- Ejecutar en pgAdmin conectado a la base "postgres" con el usuario postgres.
--
-- IMPORTANTE (pgAdmin): CREATE DATABASE no se puede ejecutar junto con otras
-- sentencias. Ejecuta cada sentencia POR SEPARADO: selecciónala y presiona F5.
--
-- La contraseña NO va en este archivo (ADR-0014). Después de crear el rol,
-- asígnala desde pgAdmin: Login/Group Roles > app_jjskaffee > Properties >
-- Definition > Password, y guárdala solo en tu archivo .env.
-- =====================================================================

-- 1) Usuario limitado de la aplicación (SPIKE-03): sin superusuario y sin BYPASSRLS,
--    para que Row Level Security siempre se aplique a sus consultas.
CREATE ROLE app_jjskaffee
    LOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    NOREPLICATION
    NOBYPASSRLS
    CONNECTION LIMIT 30;

-- 2) Base de datos nueva (no toca ninguna otra base del servidor).
CREATE DATABASE jjskaffee
    WITH ENCODING 'UTF8'
    TEMPLATE template0;
