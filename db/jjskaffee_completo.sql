-- =====================================================================
-- JJSKaffee · Base de datos COMPLETA para pgAdmin (un solo script)
-- Funcionalidad: registro de ingresos y egresos (RF14, RF18, RF24–RF27)
--
-- CÓMO USARLO EN pgAdmin:
--   1. Crear la base de datos "jjskaffee" (clic derecho en Databases > Create > Database).
--   2. Clic derecho en "jjskaffee" > Query Tool.
--   3. Abrir este archivo (ícono de carpeta) o pegarlo, y ejecutar con F5.
--
-- Se puede ejecutar varias veces: borra y vuelve a crear SOLO el esquema
-- "jjskaffee" de esta base de datos (los datos de prueba se recargan).
--
-- Usuario que debe usar el backend:
--   usuario:    app_jjskaffee
--   contraseña: cambiar_en_desarrollo   (solo desarrollo · ADR-0014)
-- =====================================================================

DROP SCHEMA IF EXISTS jjskaffee CASCADE;

-- =====================================================================
-- JJSKaffee · Base de datos · 01_esquema.sql
-- Funcionalidad: registro de ingresos y egresos (RF14, RF18, RF24–RF27)
-- Motor: PostgreSQL 15+ (Cloud SQL en producción · ADR-0012, ADR-0013)
--
-- Decisiones aplicadas:
--   ADR-0001 / ADR-0012  Todas las tablas de negocio llevan id_cafeteria
--                        (multi-tenant) y se protegen con RLS (02_rls.sql).
--   ADR-0002             Venta + detalle se guardan en una transacción;
--                        las FK y CHECK garantizan consistencia.
--   ADR-0003             Categorías propias por cafetería, se archivan
--                        (archivada = true), no se borran.
--   ADR-0004             Índices por (id_cafeteria, fecha_hora) e
--                        (id_cafeteria, id_categoria) para reportes.
--   ADR-0007             registrado_por / id_cajero y fecha_hora en cada
--                        movimiento (los llena el backend).
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS jjskaffee;
SET search_path TO jjskaffee, public;

-- ---------------------------------------------------------------------
-- Cafetería (tenant)
-- ---------------------------------------------------------------------
CREATE TABLE cafeteria (
    id_cafeteria   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre         VARCHAR(120) NOT NULL,
    creada_en      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- Usuario (administrador y cajero). La contraseña NO se guarda aquí:
-- la maneja Firebase Authentication (ADR-0001); se enlaza por firebase_uid.
-- ---------------------------------------------------------------------
CREATE TABLE usuario (
    id_usuario     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_cafeteria   UUID NOT NULL REFERENCES cafeteria (id_cafeteria),
    firebase_uid   VARCHAR(128) NOT NULL UNIQUE,
    nombre         VARCHAR(80)  NOT NULL,
    apellido       VARCHAR(80)  NOT NULL,
    nombre_usuario VARCHAR(60)  NOT NULL UNIQUE,
    rol            VARCHAR(15)  NOT NULL CHECK (rol IN ('ADMINISTRADOR', 'CAJERO')),
    activo         BOOLEAN      NOT NULL DEFAULT true,
    creado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (id_cafeteria, id_usuario)
);

-- ---------------------------------------------------------------------
-- Categoría (RF18 · ADR-0003): de ingreso, de egreso o del menú
-- ---------------------------------------------------------------------
CREATE TABLE categoria (
    id_categoria   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_cafeteria   UUID NOT NULL REFERENCES cafeteria (id_cafeteria),
    nombre         VARCHAR(80) NOT NULL,
    tipo           VARCHAR(10) NOT NULL CHECK (tipo IN ('INGRESO', 'EGRESO', 'MENU')),
    archivada      BOOLEAN     NOT NULL DEFAULT false,
    creada_en      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (id_cafeteria, id_categoria)
);
CREATE UNIQUE INDEX ux_categoria_nombre ON categoria (id_cafeteria, tipo, lower(nombre));

-- ---------------------------------------------------------------------
-- Producto del menú: base o adición (RF22, RF23)
-- ---------------------------------------------------------------------
CREATE TABLE producto (
    id_producto    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_cafeteria   UUID NOT NULL,
    id_categoria   UUID NOT NULL,
    nombre         VARCHAR(100)  NOT NULL,
    precio         NUMERIC(12,2) NOT NULL CHECK (precio >= 0),
    tipo           VARCHAR(10)   NOT NULL CHECK (tipo IN ('BASE', 'ADICION')),
    disponible     BOOLEAN       NOT NULL DEFAULT true,
    archivado      BOOLEAN       NOT NULL DEFAULT false,
    UNIQUE (id_cafeteria, id_producto),
    FOREIGN KEY (id_cafeteria, id_categoria) REFERENCES categoria (id_cafeteria, id_categoria)
);

-- Qué adiciones se pueden agregar a cada producto base (N:M)
CREATE TABLE producto_adicion (
    id_cafeteria      UUID NOT NULL,
    id_producto_base  UUID NOT NULL,
    id_adicion        UUID NOT NULL,
    obligatoria       BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (id_producto_base, id_adicion),
    FOREIGN KEY (id_cafeteria, id_producto_base) REFERENCES producto (id_cafeteria, id_producto),
    FOREIGN KEY (id_cafeteria, id_adicion)       REFERENCES producto (id_cafeteria, id_producto),
    CHECK (id_producto_base <> id_adicion)
);

-- ---------------------------------------------------------------------
-- INGRESOS: venta + detalle (RF24–RF27 · ADR-0002, ADR-0007)
-- ---------------------------------------------------------------------
CREATE TABLE venta (
    id_venta             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_cafeteria         UUID NOT NULL,
    id_cajero            UUID NOT NULL,
    fecha_hora           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    total                NUMERIC(12,2) NOT NULL CHECK (total >= 0),
    estado               VARCHAR(10)   NOT NULL DEFAULT 'CONFIRMADA'
                         CHECK (estado IN ('CONFIRMADA', 'ANULADA')),
    motivo_anulacion     VARCHAR(200),
    usuario_autorizador  UUID,
    anulada_en           TIMESTAMPTZ,
    UNIQUE (id_cafeteria, id_venta),
    FOREIGN KEY (id_cafeteria, id_cajero)           REFERENCES usuario (id_cafeteria, id_usuario),
    FOREIGN KEY (id_cafeteria, usuario_autorizador) REFERENCES usuario (id_cafeteria, id_usuario),
    -- Si está anulada, debe tener motivo, autorizador y fecha de anulación
    CHECK (estado = 'CONFIRMADA'
           OR (motivo_anulacion IS NOT NULL AND usuario_autorizador IS NOT NULL AND anulada_en IS NOT NULL))
);

CREATE TABLE detalle_venta (
    id_detalle        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_cafeteria      UUID NOT NULL,
    id_venta          UUID NOT NULL,
    id_producto       UUID NOT NULL,
    id_detalle_base   UUID REFERENCES detalle_venta (id_detalle),  -- si es adición: línea del producto base
    cantidad          INTEGER       NOT NULL CHECK (cantidad > 0),
    precio_unitario   NUMERIC(12,2) NOT NULL CHECK (precio_unitario >= 0),  -- precio al momento de vender (IMP18-03)
    subtotal          NUMERIC(12,2) GENERATED ALWAYS AS (cantidad * precio_unitario) STORED,
    FOREIGN KEY (id_cafeteria, id_venta)    REFERENCES venta (id_cafeteria, id_venta),
    FOREIGN KEY (id_cafeteria, id_producto) REFERENCES producto (id_cafeteria, id_producto)
);

-- ---------------------------------------------------------------------
-- EGRESOS (RF14 · ADR-0007)
-- ---------------------------------------------------------------------
CREATE TABLE egreso (
    id_egreso       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_cafeteria    UUID NOT NULL,
    id_categoria    UUID NOT NULL,
    monto           NUMERIC(12,2) NOT NULL CHECK (monto > 0),
    descripcion     VARCHAR(200)  NOT NULL,
    comprobante     VARCHAR(50),
    registrado_por  UUID NOT NULL,
    fecha_hora      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    FOREIGN KEY (id_cafeteria, id_categoria)   REFERENCES categoria (id_cafeteria, id_categoria),
    FOREIGN KEY (id_cafeteria, registrado_por) REFERENCES usuario (id_cafeteria, id_usuario)
);

-- La categoría de un egreso debe ser de tipo EGRESO y estar activa
CREATE FUNCTION fn_validar_categoria_egreso() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM categoria c
                   WHERE c.id_categoria = NEW.id_categoria
                     AND c.tipo = 'EGRESO' AND NOT c.archivada) THEN
        RAISE EXCEPTION 'La categoría % no es de egreso o está archivada', NEW.id_categoria
              USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_egreso_categoria
    BEFORE INSERT OR UPDATE OF id_categoria ON egreso
    FOR EACH ROW EXECUTE FUNCTION fn_validar_categoria_egreso();

-- ---------------------------------------------------------------------
-- Índices para consultas y reportes (ADR-0004)
-- ---------------------------------------------------------------------
CREATE INDEX ix_venta_cafeteria_fecha    ON venta (id_cafeteria, fecha_hora);
CREATE INDEX ix_detalle_venta_venta      ON detalle_venta (id_venta);
CREATE INDEX ix_egreso_cafeteria_fecha   ON egreso (id_cafeteria, fecha_hora);
CREATE INDEX ix_egreso_cafeteria_cat     ON egreso (id_cafeteria, id_categoria);
CREATE INDEX ix_producto_cafeteria       ON producto (id_cafeteria) WHERE NOT archivado;

-- ---------------------------------------------------------------------
-- Vista de movimientos: ingresos (ventas confirmadas) y egresos juntos.
-- Es la "única fuente de verdad" para GET /movimientos y los reportes.
-- security_invoker = true → respeta el RLS del usuario que consulta.
-- ---------------------------------------------------------------------
CREATE VIEW v_movimiento WITH (security_invoker = true) AS
    SELECT v.id_venta        AS id_movimiento,
           v.id_cafeteria,
           'INGRESO'::text   AS tipo,
           v.fecha_hora,
           v.total           AS monto,
           'Venta'::text     AS descripcion,
           NULL::uuid        AS id_categoria,
           v.id_cajero       AS registrado_por
    FROM venta v
    WHERE v.estado = 'CONFIRMADA'
UNION ALL
    SELECT e.id_egreso, e.id_cafeteria, 'EGRESO', e.fecha_hora, e.monto,
           e.descripcion, e.id_categoria, e.registrado_por
    FROM egreso e;


-- =====================================================================
-- PARTE 2 · Seguridad por cafetería (RLS) y usuario de la aplicación
-- =====================================================================
-- =====================================================================
-- JJSKaffee · Base de datos · 02_rls.sql
-- Aislamiento multi-tenant con Row Level Security (ADR-0001, ADR-0012)
-- Validado en SPIKE-03: hace falta FORCE ROW LEVEL SECURITY y que la
-- aplicación se conecte con un usuario limitado (nunca superusuario).
--
-- Cómo lo usa el backend (PgUnidadDeTrabajo), en cada transacción:
--   BEGIN;
--   SELECT set_config('app.current_cafeteria', '<uuid de la cafetería>', true);
--   ... INSERT / SELECT ...
--   COMMIT;
-- =====================================================================
SET search_path TO jjskaffee, public;

-- Cafetería del contexto actual (NULL si no se fijó → no ve nada)
CREATE FUNCTION cafeteria_actual() RETURNS uuid
LANGUAGE sql STABLE AS $$
    SELECT NULLIF(current_setting('app.current_cafeteria', true), '')::uuid
$$;

-- Activar y forzar RLS en todas las tablas con datos de cafeterías
DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['cafeteria','usuario','categoria','producto',
                             'producto_adicion','venta','detalle_venta','egreso'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY p_aislamiento_cafeteria ON %I
                 USING (id_cafeteria = cafeteria_actual())
                 WITH CHECK (id_cafeteria = cafeteria_actual())', t);
    END LOOP;
END $$;

-- ---------------------------------------------------------------------
-- Rol de la aplicación: limitado, sin DELETE (borrado lógico · IMP18-04).
-- La contraseña real se guarda en Google Cloud Secret Manager (ADR-0014);
-- aquí va un valor de desarrollo que se cambia al desplegar.
-- ---------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_jjskaffee') THEN
        CREATE ROLE app_jjskaffee LOGIN PASSWORD 'cambiar_en_desarrollo'
            NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    ELSE
        -- Ya existía: se deja con la contraseña de desarrollo y sin privilegios especiales
        ALTER ROLE app_jjskaffee LOGIN PASSWORD 'cambiar_en_desarrollo'
            NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;
END $$;

GRANT USAGE ON SCHEMA jjskaffee TO app_jjskaffee;
GRANT SELECT ON ALL TABLES IN SCHEMA jjskaffee TO app_jjskaffee;
GRANT INSERT ON venta, detalle_venta, egreso, categoria, producto, producto_adicion TO app_jjskaffee;
-- UPDATE solo donde el negocio lo necesita: anular ventas, archivar categorías/productos
GRANT UPDATE (estado, motivo_anulacion, usuario_autorizador, anulada_en) ON venta TO app_jjskaffee;
GRANT UPDATE (archivada, nombre) ON categoria TO app_jjskaffee;
GRANT UPDATE (nombre, precio, disponible, archivado) ON producto TO app_jjskaffee;
GRANT EXECUTE ON FUNCTION cafeteria_actual() TO app_jjskaffee;
-- Ningún GRANT DELETE: los movimientos financieros no se borran.


-- =====================================================================
-- PARTE 3 · Datos de prueba
-- =====================================================================
-- =====================================================================
-- JJSKaffee · Base de datos · 03_datos_prueba.sql
-- Datos de desarrollo: 2 cafeterías (para comprobar el aislamiento),
-- usuarios, categorías, menú con adiciones, ventas y egresos.
-- Los UUID son fijos para que coincidan con el modo simulado del frontend.
-- =====================================================================
SET search_path TO jjskaffee, public;

-- ============ Cafetería 1: Café Demo ============
SELECT set_config('app.current_cafeteria', '11111111-1111-1111-1111-111111111111', false);

INSERT INTO cafeteria (id_cafeteria, nombre)
VALUES ('11111111-1111-1111-1111-111111111111', 'Café Demo');

INSERT INTO usuario (id_usuario, id_cafeteria, firebase_uid, nombre, apellido, nombre_usuario, rol) VALUES
('a0000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'firebase-admin-demo',  'Laura',  'Gómez', 'admin.demo',  'ADMINISTRADOR'),
('a0000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'firebase-cajero-demo', 'Andrés', 'Ruiz',  'cajero.demo', 'CAJERO');

INSERT INTO categoria (id_categoria, id_cafeteria, nombre, tipo, archivada) VALUES
('c0000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Bebidas calientes',      'MENU',   false),
('c0000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Panadería',              'MENU',   false),
('c0000000-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'Adiciones',              'MENU',   false),
('c0000000-0000-0000-0000-000000000011', '11111111-1111-1111-1111-111111111111', 'Insumos',                'EGRESO', false),
('c0000000-0000-0000-0000-000000000012', '11111111-1111-1111-1111-111111111111', 'Servicios públicos',     'EGRESO', false),
('c0000000-0000-0000-0000-000000000013', '11111111-1111-1111-1111-111111111111', 'Arriendo',               'EGRESO', false),
('c0000000-0000-0000-0000-000000000014', '11111111-1111-1111-1111-111111111111', 'Nómina',                 'EGRESO', false),
('c0000000-0000-0000-0000-000000000015', '11111111-1111-1111-1111-111111111111', 'Mantenimiento (antigua)', 'EGRESO', true);

INSERT INTO producto (id_producto, id_cafeteria, id_categoria, nombre, precio, tipo) VALUES
('b0000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'c0000000-0000-0000-0000-000000000001', 'Café americano',         4000, 'BASE'),
('b0000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'c0000000-0000-0000-0000-000000000001', 'Café latte',             6500, 'BASE'),
('b0000000-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'c0000000-0000-0000-0000-000000000001', 'Capuchino',              7000, 'BASE'),
('b0000000-0000-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', 'c0000000-0000-0000-0000-000000000001', 'Chocolate caliente',     5500, 'BASE'),
('b0000000-0000-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111', 'c0000000-0000-0000-0000-000000000002', 'Pandebono',              2500, 'BASE'),
('b0000000-0000-0000-0000-000000000006', '11111111-1111-1111-1111-111111111111', 'c0000000-0000-0000-0000-000000000002', 'Croissant',              4500, 'BASE'),
('b0000000-0000-0000-0000-000000000011', '11111111-1111-1111-1111-111111111111', 'c0000000-0000-0000-0000-000000000003', 'Leche de almendra',      1500, 'ADICION'),
('b0000000-0000-0000-0000-000000000012', '11111111-1111-1111-1111-111111111111', 'c0000000-0000-0000-0000-000000000003', 'Shot extra de espresso', 2000, 'ADICION'),
('b0000000-0000-0000-0000-000000000013', '11111111-1111-1111-1111-111111111111', 'c0000000-0000-0000-0000-000000000003', 'Crema batida',           1200, 'ADICION'),
('b0000000-0000-0000-0000-000000000014', '11111111-1111-1111-1111-111111111111', 'c0000000-0000-0000-0000-000000000003', 'Jarabe de vainilla',     1000, 'ADICION');

INSERT INTO producto_adicion (id_cafeteria, id_producto_base, id_adicion) VALUES
('11111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000011'),
('11111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000012'),
('11111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000011'),
('11111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000012'),
('11111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000014'),
('11111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000011'),
('11111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000012'),
('11111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000013'),
('11111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000004', 'b0000000-0000-0000-0000-000000000011'),
('11111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000004', 'b0000000-0000-0000-0000-000000000013');

-- Venta 1: 2 cafés latte, uno con leche de almendra  →  2×6500 + 1×1500 = 14 500
INSERT INTO venta (id_venta, id_cafeteria, id_cajero, fecha_hora, total) VALUES
('d0000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
 'a0000000-0000-0000-0000-000000000002', now() - interval '2 hours', 14500);
INSERT INTO detalle_venta (id_detalle, id_cafeteria, id_venta, id_producto, id_detalle_base, cantidad, precio_unitario) VALUES
('e0000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'd0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000002', NULL, 2, 6500),
('e0000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'd0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000011', 'e0000000-0000-0000-0000-000000000001', 1, 1500);

-- Venta 2: 1 capuchino + 2 pandebonos  →  7000 + 2×2500 = 12 000
INSERT INTO venta (id_venta, id_cafeteria, id_cajero, fecha_hora, total) VALUES
('d0000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111',
 'a0000000-0000-0000-0000-000000000002', now() - interval '1 hour', 12000);
INSERT INTO detalle_venta (id_cafeteria, id_venta, id_producto, cantidad, precio_unitario) VALUES
('11111111-1111-1111-1111-111111111111', 'd0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000003', 1, 7000),
('11111111-1111-1111-1111-111111111111', 'd0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000005', 2, 2500);

INSERT INTO egreso (id_cafeteria, id_categoria, monto, descripcion, comprobante, registrado_por, fecha_hora) VALUES
('11111111-1111-1111-1111-111111111111', 'c0000000-0000-0000-0000-000000000011', 45000,  'Compra de leche y café en grano', 'FAC-1023', 'a0000000-0000-0000-0000-000000000001', now() - interval '3 hours'),
('11111111-1111-1111-1111-111111111111', 'c0000000-0000-0000-0000-000000000012', 120000, 'Factura de energía',              'EPM-0925', 'a0000000-0000-0000-0000-000000000001', now() - interval '1 day');

-- ============ Cafetería 2: Café Vecino (para probar el aislamiento) ============
SELECT set_config('app.current_cafeteria', '22222222-2222-2222-2222-222222222222', false);

INSERT INTO cafeteria (id_cafeteria, nombre)
VALUES ('22222222-2222-2222-2222-222222222222', 'Café Vecino');
INSERT INTO usuario (id_usuario, id_cafeteria, firebase_uid, nombre, apellido, nombre_usuario, rol) VALUES
('a0000000-0000-0000-0000-000000000003', '22222222-2222-2222-2222-222222222222', 'firebase-admin-vecino', 'Marta', 'López', 'admin.vecino', 'ADMINISTRADOR');
INSERT INTO categoria (id_categoria, id_cafeteria, nombre, tipo) VALUES
('c0000000-0000-0000-0000-000000000021', '22222222-2222-2222-2222-222222222222', 'Insumos', 'EGRESO');
INSERT INTO egreso (id_cafeteria, id_categoria, monto, descripcion, registrado_por) VALUES
('22222222-2222-2222-2222-222222222222', 'c0000000-0000-0000-0000-000000000021', 99999, 'Dato privado del Café Vecino', 'a0000000-0000-0000-0000-000000000003');

SELECT set_config('app.current_cafeteria', '', false);


-- =====================================================================
-- PARTE 4 · Conexión del backend
-- =====================================================================
-- Permiso para conectarse a ESTA base de datos (sea cual sea su nombre)
DO $$ BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO app_jjskaffee', current_database());
END $$;

-- Que el backend encuentre las tablas sin escribir "jjskaffee." delante
ALTER ROLE app_jjskaffee SET search_path = jjskaffee, public;
-- Todas las fechas de la sesión en UTC (fechas en UTC-0)
ALTER ROLE app_jjskaffee SET timezone = 'UTC';

-- =====================================================================
-- Resumen: debe mostrar las tablas con su cantidad de filas
-- =====================================================================
SELECT 'cafeteria' AS tabla, count(*) AS filas FROM jjskaffee.cafeteria
UNION ALL SELECT 'usuario', count(*) FROM jjskaffee.usuario
UNION ALL SELECT 'categoria', count(*) FROM jjskaffee.categoria
UNION ALL SELECT 'producto', count(*) FROM jjskaffee.producto
UNION ALL SELECT 'producto_adicion', count(*) FROM jjskaffee.producto_adicion
UNION ALL SELECT 'venta', count(*) FROM jjskaffee.venta
UNION ALL SELECT 'detalle_venta', count(*) FROM jjskaffee.detalle_venta
UNION ALL SELECT 'egreso', count(*) FROM jjskaffee.egreso;
