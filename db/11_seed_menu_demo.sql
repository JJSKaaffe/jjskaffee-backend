-- =====================================================================
-- JJSKaffee · 11 · Datos de demostración: menú completo (cafeterías A y B)
-- Ejecutar en pgAdmin (Query Tool sobre "jjskaffee", todo el archivo con F5) o con
-- psql -v ON_ERROR_STOP=1 -d jjskaffee -f db/11_seed_menu_demo.sql, con el usuario
-- postgres, después de 10_seed_categorias_ingreso.sql.
--
-- Es para la sustentación, NO para las pruebas: no corre en el CI. CatalogoDeVentaIT
-- espera el catálogo exacto de db/03 y db/10 (en B solo el Capuchino y ninguna
-- categoría de ingreso; el latte de A solo con almendra y shot). Con este script
-- cargado, ./mvnw verify falla en esa base: las pruebas *IT necesitan una sin db/11.
--
-- Qué hace (el mismo menú en A y en B):
--   * Categorías de menú Bebidas calientes, Bebidas frías, Panadería y Postres.
--   * 14 productos base y 8 adiciones, con precios en pesos, y qué adición va con
--     qué producto (producto_adicion).
--   * Categorías de ingreso activas Eventos privados y Venta de café en grano
--     (A ya las tiene por db/10; aquí se le agregan a B).
--
-- Respeta lo que ya existe y se puede correr varias veces: cada INSERT salta lo que
-- ya hay en esa cafetería con el mismo nombre (categorías: mismo tipo y nombre).
-- Por eso no duplica el Espresso, el Café latte, el Pandebono, la Leche de almendra
-- ni el Shot extra de espresso de A, ni el Capuchino de B (que sigue en su categoría
-- "Bebidas"). Los productos nuevos llevan UUID aleatorios: las pruebas no los usan.
--
-- RLS: las tablas tienen FORCE ROW LEVEL SECURITY y en Cloud SQL postgres no es
-- superusuario. Cada cafetería va en su propia transacción y fija
-- app.current_cafeteria al inicio, como PgUnidadDeTrabajo: así los INSERT pasan
-- el WITH CHECK de las políticas sin quitar el FORCE. Las consultas filtran además
-- por app_cafeteria_actual() para que el resultado sea el mismo en local, donde
-- postgres es superusuario y se salta el RLS.
--
-- El menú se escribe una sola vez en tablas temporales (de la sesión, sin RLS) y
-- cada cafetería lo copia; al final se borran.
-- =====================================================================

-- ---------------------------------------------------------------------
-- El menú de demostración (tablas temporales)
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS pg_temp.demo_categoria, pg_temp.demo_producto,
                     pg_temp.demo_adicion, pg_temp.demo_resumen;

CREATE TEMP TABLE demo_categoria (
    nombre  varchar(60) NOT NULL,
    tipo    varchar(10) NOT NULL
);

CREATE TEMP TABLE demo_producto (
    nombre     varchar(80)   NOT NULL,
    categoria  varchar(60)   NOT NULL,
    precio     numeric(12,2) NOT NULL,
    tipo       varchar(10)   NOT NULL
);

CREATE TEMP TABLE demo_adicion (
    producto_base  varchar(80) NOT NULL,
    adicion        varchar(80) NOT NULL
);

CREATE TEMP TABLE demo_resumen (
    cafeteria               varchar(100),
    productos_base          bigint,
    adiciones               bigint,
    adiciones_por_producto  bigint,
    categorias_menu         bigint,
    categorias_ingreso      bigint
);

INSERT INTO demo_categoria (nombre, tipo) VALUES
    ('Bebidas calientes',      'MENU'),
    ('Bebidas frías',          'MENU'),
    ('Panadería',              'MENU'),
    ('Postres',                'MENU'),
    ('Eventos privados',       'INGRESO'),
    ('Venta de café en grano', 'INGRESO');

INSERT INTO demo_producto (nombre, categoria, precio, tipo) VALUES
    -- Productos base
    ('Tinto',                  'Bebidas calientes',  2500, 'BASE'),
    ('Espresso',               'Bebidas calientes',  4000, 'BASE'),
    ('Americano',              'Bebidas calientes',  4500, 'BASE'),
    ('Café latte',             'Bebidas calientes',  6500, 'BASE'),
    ('Capuchino',              'Bebidas calientes',  7000, 'BASE'),
    ('Chocolate caliente',     'Bebidas calientes',  6000, 'BASE'),
    ('Café frappé',            'Bebidas frías',      9500, 'BASE'),
    ('Cold brew',              'Bebidas frías',      8500, 'BASE'),
    ('Jugo natural de mora',   'Bebidas frías',      7000, 'BASE'),
    ('Pandebono',              'Panadería',          2500, 'BASE'),
    ('Almojábana',             'Panadería',          3000, 'BASE'),
    ('Croissant',              'Panadería',          5500, 'BASE'),
    ('Torta de chocolate',     'Postres',            9000, 'BASE'),
    ('Cheesecake de maracuyá', 'Postres',           10500, 'BASE'),
    -- Adiciones
    ('Shot extra de espresso', 'Bebidas calientes',  2000, 'ADICION'),
    ('Leche de almendra',      'Bebidas calientes',  1500, 'ADICION'),
    ('Leche deslactosada',     'Bebidas calientes',  1000, 'ADICION'),
    ('Sirope de vainilla',     'Bebidas calientes',  1500, 'ADICION'),
    ('Malvaviscos',            'Bebidas calientes',  1000, 'ADICION'),
    ('Queso extra',            'Panadería',          1500, 'ADICION'),
    ('Crema batida',           'Postres',            1500, 'ADICION'),
    ('Helado de vainilla',     'Postres',            3000, 'ADICION');

INSERT INTO demo_adicion (producto_base, adicion) VALUES
    ('Espresso',               'Shot extra de espresso'),
    ('Americano',              'Shot extra de espresso'),
    ('Americano',              'Sirope de vainilla'),
    ('Café latte',             'Shot extra de espresso'),
    ('Café latte',             'Leche de almendra'),
    ('Café latte',             'Leche deslactosada'),
    ('Café latte',             'Sirope de vainilla'),
    ('Capuchino',              'Shot extra de espresso'),
    ('Capuchino',              'Leche de almendra'),
    ('Capuchino',              'Leche deslactosada'),
    ('Capuchino',              'Sirope de vainilla'),
    ('Chocolate caliente',     'Leche de almendra'),
    ('Chocolate caliente',     'Leche deslactosada'),
    ('Chocolate caliente',     'Crema batida'),
    ('Chocolate caliente',     'Malvaviscos'),
    ('Café frappé',            'Shot extra de espresso'),
    ('Café frappé',            'Leche de almendra'),
    ('Café frappé',            'Leche deslactosada'),
    ('Café frappé',            'Sirope de vainilla'),
    ('Café frappé',            'Crema batida'),
    ('Cold brew',              'Shot extra de espresso'),
    ('Cold brew',              'Sirope de vainilla'),
    ('Pandebono',              'Queso extra'),
    ('Almojábana',             'Queso extra'),
    ('Croissant',              'Queso extra'),
    ('Torta de chocolate',     'Crema batida'),
    ('Torta de chocolate',     'Helado de vainilla'),
    ('Cheesecake de maracuyá', 'Crema batida'),
    ('Cheesecake de maracuyá', 'Helado de vainilla');

-- ---------------------------------------------------------------------
-- Cafetería A
-- (Mismas sentencias que en B; solo cambia la cafetería fijada.)
-- ---------------------------------------------------------------------
BEGIN;
SELECT set_config('app.current_cafeteria', '11111111-1111-1111-1111-111111111111', true);

INSERT INTO categoria (id, id_cafeteria, nombre, tipo, archivada)
SELECT gen_random_uuid(), app_cafeteria_actual(), d.nombre, d.tipo, false
FROM demo_categoria d
WHERE NOT EXISTS (SELECT 1 FROM categoria c
                  WHERE c.id_cafeteria = app_cafeteria_actual()
                    AND c.tipo = d.tipo AND c.nombre = d.nombre);

INSERT INTO producto (id, id_cafeteria, id_categoria, nombre, precio, tipo, disponible)
SELECT gen_random_uuid(), app_cafeteria_actual(), c.id, d.nombre, d.precio, d.tipo, true
FROM demo_producto d
JOIN categoria c ON c.id_cafeteria = app_cafeteria_actual() AND c.tipo = 'MENU' AND c.nombre = d.categoria
WHERE NOT EXISTS (SELECT 1 FROM producto p
                  WHERE p.id_cafeteria = app_cafeteria_actual() AND p.nombre = d.nombre);

INSERT INTO producto_adicion (id_cafeteria, id_producto_base, id_adicion)
SELECT app_cafeteria_actual(), b.id, a.id
FROM demo_adicion d
JOIN producto b ON b.id_cafeteria = app_cafeteria_actual() AND b.tipo = 'BASE'    AND b.nombre = d.producto_base
JOIN producto a ON a.id_cafeteria = app_cafeteria_actual() AND a.tipo = 'ADICION' AND a.nombre = d.adicion
WHERE NOT EXISTS (SELECT 1 FROM producto_adicion pa
                  WHERE pa.id_producto_base = b.id AND pa.id_adicion = a.id);

COMMIT;

-- ---------------------------------------------------------------------
-- Cafetería B
-- ---------------------------------------------------------------------
BEGIN;
SELECT set_config('app.current_cafeteria', '22222222-2222-2222-2222-222222222222', true);

INSERT INTO categoria (id, id_cafeteria, nombre, tipo, archivada)
SELECT gen_random_uuid(), app_cafeteria_actual(), d.nombre, d.tipo, false
FROM demo_categoria d
WHERE NOT EXISTS (SELECT 1 FROM categoria c
                  WHERE c.id_cafeteria = app_cafeteria_actual()
                    AND c.tipo = d.tipo AND c.nombre = d.nombre);

INSERT INTO producto (id, id_cafeteria, id_categoria, nombre, precio, tipo, disponible)
SELECT gen_random_uuid(), app_cafeteria_actual(), c.id, d.nombre, d.precio, d.tipo, true
FROM demo_producto d
JOIN categoria c ON c.id_cafeteria = app_cafeteria_actual() AND c.tipo = 'MENU' AND c.nombre = d.categoria
WHERE NOT EXISTS (SELECT 1 FROM producto p
                  WHERE p.id_cafeteria = app_cafeteria_actual() AND p.nombre = d.nombre);

INSERT INTO producto_adicion (id_cafeteria, id_producto_base, id_adicion)
SELECT app_cafeteria_actual(), b.id, a.id
FROM demo_adicion d
JOIN producto b ON b.id_cafeteria = app_cafeteria_actual() AND b.tipo = 'BASE'    AND b.nombre = d.producto_base
JOIN producto a ON a.id_cafeteria = app_cafeteria_actual() AND a.tipo = 'ADICION' AND a.nombre = d.adicion
WHERE NOT EXISTS (SELECT 1 FROM producto_adicion pa
                  WHERE pa.id_producto_base = b.id AND pa.id_adicion = a.id);

COMMIT;

-- ---------------------------------------------------------------------
-- Resumen: qué quedó en cada cafetería. Cada una se cuenta en su propia
-- transacción con su cafetería fijada y el resultado se junta en una sola tabla.
-- Esperado: A y B con 14 productos base, 8 adiciones, 29 adiciones por producto
-- y 2 categorías de ingreso activas; categorías de menú, 4 en A y 5 en B (la
-- "Bebidas" de db/03).
-- ---------------------------------------------------------------------
BEGIN;
SELECT set_config('app.current_cafeteria', '11111111-1111-1111-1111-111111111111', true);
INSERT INTO demo_resumen
SELECT c.nombre,
       (SELECT count(*) FROM producto p WHERE p.id_cafeteria = c.id AND p.tipo = 'BASE'),
       (SELECT count(*) FROM producto p WHERE p.id_cafeteria = c.id AND p.tipo = 'ADICION'),
       (SELECT count(*) FROM producto_adicion pa WHERE pa.id_cafeteria = c.id),
       (SELECT count(*) FROM categoria k WHERE k.id_cafeteria = c.id AND k.tipo = 'MENU'    AND NOT k.archivada),
       (SELECT count(*) FROM categoria k WHERE k.id_cafeteria = c.id AND k.tipo = 'INGRESO' AND NOT k.archivada)
FROM cafeteria c
WHERE c.id = app_cafeteria_actual();
COMMIT;

BEGIN;
SELECT set_config('app.current_cafeteria', '22222222-2222-2222-2222-222222222222', true);
INSERT INTO demo_resumen
SELECT c.nombre,
       (SELECT count(*) FROM producto p WHERE p.id_cafeteria = c.id AND p.tipo = 'BASE'),
       (SELECT count(*) FROM producto p WHERE p.id_cafeteria = c.id AND p.tipo = 'ADICION'),
       (SELECT count(*) FROM producto_adicion pa WHERE pa.id_cafeteria = c.id),
       (SELECT count(*) FROM categoria k WHERE k.id_cafeteria = c.id AND k.tipo = 'MENU'    AND NOT k.archivada),
       (SELECT count(*) FROM categoria k WHERE k.id_cafeteria = c.id AND k.tipo = 'INGRESO' AND NOT k.archivada)
FROM cafeteria c
WHERE c.id = app_cafeteria_actual();
COMMIT;

-- Última sentencia a propósito, para que pgAdmin muestre este resultado. Las
-- tablas temporales se borran solas al cerrar la sesión (y al inicio, si se repite).
SELECT * FROM demo_resumen ORDER BY cafeteria;
