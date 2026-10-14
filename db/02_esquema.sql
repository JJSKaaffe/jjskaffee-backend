-- =====================================================================
-- JJSKaffee · 02 · Esquema de ingresos y egresos con Row Level Security
-- Ejecutar en pgAdmin conectado a la base "jjskaffee" con el usuario postgres
-- (abre el Query Tool sobre jjskaffee y ejecuta todo el archivo con F5).
--
-- Las tablas son de postgres; la aplicación entra con app_jjskaffee, que solo
-- tiene los permisos mínimos y siempre queda sujeta a RLS (ADR-0012, SPIKE-03).
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- Permisos sobre la base y el esquema
-- ---------------------------------------------------------------------
REVOKE ALL ON DATABASE jjskaffee FROM PUBLIC;
GRANT CONNECT ON DATABASE jjskaffee TO app_jjskaffee;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO app_jjskaffee;

-- ---------------------------------------------------------------------
-- Cafetería activa de la transacción.
-- PgUnidadDeTrabajo la fija con set_config('app.current_cafeteria', id, true),
-- que dura solo hasta el fin de la transacción. Si no está fijada devuelve NULL
-- y las políticas no dejan ver ni escribir nada.
-- (NULLIF: después de una transacción la variable queda en '' y no en NULL.)
-- ---------------------------------------------------------------------
CREATE FUNCTION app_cafeteria_actual() RETURNS uuid
    LANGUAGE sql STABLE
AS $$
    SELECT NULLIF(current_setting('app.current_cafeteria', true), '')::uuid
$$;

-- ---------------------------------------------------------------------
-- Tablas
-- Las llaves foráneas incluyen id_cafeteria para que un registro nunca pueda
-- apuntar a datos de otra cafetería (las FK no pasan por RLS).
-- ---------------------------------------------------------------------
CREATE TABLE cafeteria (
    id          uuid PRIMARY KEY,
    nombre      varchar(100) NOT NULL,
    creada_en   timestamptz  NOT NULL DEFAULT now()
);

-- Usuarios de la cafetería: el administrador y los cajeros (nombre del MER: "usuarios").
-- Los crea el módulo de autenticación; este backend solo los lee para pasar
-- del uid de Firebase al id, nombre y rol (ADR-0001).
CREATE TABLE usuarios (
    id            uuid         PRIMARY KEY,
    id_cafeteria  uuid         NOT NULL REFERENCES cafeteria (id),
    firebase_uid  varchar(128) NOT NULL UNIQUE,
    nombre        varchar(100) NOT NULL,
    rol           varchar(20)  NOT NULL CHECK (rol IN ('ADMINISTRADOR', 'CAJERO')),
    activo        boolean      NOT NULL DEFAULT true,
    UNIQUE (id_cafeteria, id)
);

-- Categorías por cafetería (ADR-0003). Se archivan, no se borran.
CREATE TABLE categoria (
    id            uuid        PRIMARY KEY,
    id_cafeteria  uuid        NOT NULL REFERENCES cafeteria (id),
    nombre        varchar(60) NOT NULL,
    tipo          varchar(10) NOT NULL CHECK (tipo IN ('INGRESO', 'EGRESO', 'MENU')),
    archivada     boolean     NOT NULL DEFAULT false,
    UNIQUE (id_cafeteria, id),
    UNIQUE (id_cafeteria, tipo, nombre)
);

-- Productos del menú: base (café, pan) o adición (leche de almendra, shot extra).
CREATE TABLE producto (
    id            uuid          PRIMARY KEY,
    id_cafeteria  uuid          NOT NULL REFERENCES cafeteria (id),
    id_categoria  uuid,
    nombre        varchar(80)   NOT NULL,
    precio        numeric(12,2) NOT NULL CHECK (precio >= 0),
    tipo          varchar(10)   NOT NULL CHECK (tipo IN ('BASE', 'ADICION')),
    disponible    boolean       NOT NULL DEFAULT true,
    UNIQUE (id_cafeteria, id),
    FOREIGN KEY (id_cafeteria, id_categoria) REFERENCES categoria (id_cafeteria, id)
);

-- Qué adiciones se le pueden poner a cada producto base.
CREATE TABLE producto_adicion (
    id_cafeteria      uuid NOT NULL,
    id_producto_base  uuid NOT NULL,
    id_adicion        uuid NOT NULL,
    PRIMARY KEY (id_producto_base, id_adicion),
    FOREIGN KEY (id_cafeteria, id_producto_base) REFERENCES producto (id_cafeteria, id),
    FOREIGN KEY (id_cafeteria, id_adicion)       REFERENCES producto (id_cafeteria, id)
);

-- Venta = ingreso (RF24–RF27). El total lo calcula el servidor con los precios
-- del catálogo y se guarda para poder sumar con SQL (ADR-0004).
-- Nunca se borra: se anula con motivo (borrado lógico).
CREATE TABLE venta (
    id                uuid          PRIMARY KEY,
    id_cafeteria      uuid          NOT NULL,
    id_cajero         uuid          NOT NULL,
    fecha_hora        timestamptz   NOT NULL,
    estado            varchar(12)   NOT NULL CHECK (estado IN ('CONFIRMADA', 'ANULADA')),
    motivo_anulacion  varchar(200),
    total             numeric(12,2) NOT NULL CHECK (total >= 0),
    UNIQUE (id_cafeteria, id),
    FOREIGN KEY (id_cafeteria, id_cajero) REFERENCES usuarios (id_cafeteria, id),
    CHECK (estado = 'CONFIRMADA' OR motivo_anulacion IS NOT NULL)
);

-- Detalle de la venta con el precio del momento. Una adición apunta al
-- detalle del producto base al que se le agregó.
CREATE TABLE detalle_venta (
    id               uuid          PRIMARY KEY,
    id_cafeteria     uuid          NOT NULL,
    id_venta         uuid          NOT NULL,
    id_producto      uuid          NOT NULL,
    cantidad         integer       NOT NULL CHECK (cantidad >= 1),
    precio_unitario  numeric(12,2) NOT NULL CHECK (precio_unitario >= 0),
    id_detalle_base  uuid,
    UNIQUE (id_cafeteria, id),
    FOREIGN KEY (id_cafeteria, id_venta)        REFERENCES venta (id_cafeteria, id),
    FOREIGN KEY (id_cafeteria, id_producto)     REFERENCES producto (id_cafeteria, id),
    FOREIGN KEY (id_cafeteria, id_detalle_base) REFERENCES detalle_venta (id_cafeteria, id)
);

-- Egreso (RF14). Borrado lógico: se anula con motivo, no se elimina.
CREATE TABLE egreso (
    id                uuid          PRIMARY KEY,
    id_cafeteria      uuid          NOT NULL,
    id_categoria      uuid          NOT NULL,
    monto             numeric(12,2) NOT NULL CHECK (monto > 0),
    descripcion       varchar(200)  NOT NULL,
    comprobante       varchar(50),
    registrado_por    uuid          NOT NULL,
    fecha_hora        timestamptz   NOT NULL,
    anulado           boolean       NOT NULL DEFAULT false,
    motivo_anulacion  varchar(200),
    FOREIGN KEY (id_cafeteria, id_categoria)   REFERENCES categoria (id_cafeteria, id),
    FOREIGN KEY (id_cafeteria, registrado_por) REFERENCES usuarios (id_cafeteria, id),
    CHECK (NOT anulado OR motivo_anulacion IS NOT NULL)
);

-- ---------------------------------------------------------------------
-- Índices para filtros por cafetería, fecha, categoría y usuario (ADR-0004)
-- ---------------------------------------------------------------------
CREATE INDEX ix_venta_cafeteria_fecha          ON venta (id_cafeteria, fecha_hora DESC);
CREATE INDEX ix_venta_cafeteria_cajero_fecha   ON venta (id_cafeteria, id_cajero, fecha_hora DESC);
CREATE INDEX ix_detalle_venta_venta            ON detalle_venta (id_venta);
CREATE INDEX ix_egreso_cafeteria_fecha         ON egreso (id_cafeteria, fecha_hora DESC);
CREATE INDEX ix_egreso_cafeteria_categoria     ON egreso (id_cafeteria, id_categoria, fecha_hora DESC);
CREATE INDEX ix_egreso_cafeteria_usuario_fecha ON egreso (id_cafeteria, registrado_por, fecha_hora DESC);

-- ---------------------------------------------------------------------
-- Row Level Security: ENABLE + FORCE en TODA tabla con datos de cafetería.
-- Una sola política por tabla para leer y escribir: solo filas de la cafetería activa.
-- ---------------------------------------------------------------------
ALTER TABLE cafeteria ENABLE ROW LEVEL SECURITY;
ALTER TABLE cafeteria FORCE ROW LEVEL SECURITY;
CREATE POLICY aislamiento_cafeteria ON cafeteria
    USING (id = app_cafeteria_actual())
    WITH CHECK (id = app_cafeteria_actual());

DO $$
DECLARE
    tabla text;
BEGIN
    FOREACH tabla IN ARRAY ARRAY['usuarios', 'categoria', 'producto', 'producto_adicion',
                                 'venta', 'detalle_venta', 'egreso']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tabla);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tabla);
        EXECUTE format('CREATE POLICY aislamiento_cafeteria ON %I '
                       'USING (id_cafeteria = app_cafeteria_actual()) '
                       'WITH CHECK (id_cafeteria = app_cafeteria_actual())', tabla);
    END LOOP;
END
$$;

-- ---------------------------------------------------------------------
-- Vista de movimientos (ingresos + egresos) para la consulta con filtros (RF38).
-- security_invoker = true: la vista se evalúa con los permisos y el RLS de quien
-- consulta. Sin esto, la vista correría como su dueño (postgres) y se saltaría RLS.
-- ---------------------------------------------------------------------
CREATE VIEW v_movimiento WITH (security_invoker = true) AS
SELECT v.id,
       v.id_cafeteria,
       'INGRESO'::varchar(7)   AS tipo,
       v.fecha_hora,
       v.total                 AS monto,
       'Venta'::varchar(200)   AS descripcion,
       NULL::uuid              AS id_categoria,
       NULL::varchar(60)       AS categoria,
       v.id_cajero             AS id_registrado_por,
       u.nombre                AS registrado_por
FROM venta v
JOIN usuarios u ON u.id_cafeteria = v.id_cafeteria AND u.id = v.id_cajero
WHERE v.estado = 'CONFIRMADA'
UNION ALL
SELECT e.id,
       e.id_cafeteria,
       'EGRESO'::varchar(7),
       e.fecha_hora,
       e.monto,
       e.descripcion,
       e.id_categoria,
       c.nombre,
       e.registrado_por,
       u.nombre
FROM egreso e
JOIN categoria c ON c.id_cafeteria = e.id_cafeteria AND c.id = e.id_categoria
JOIN usuarios u  ON u.id_cafeteria = e.id_cafeteria AND u.id = e.registrado_por
WHERE NOT e.anulado;

-- ---------------------------------------------------------------------
-- Permisos mínimos de la aplicación.
-- Catálogo y usuarios: solo lectura. Movimientos: leer e insertar.
-- De la venta solo se puede cambiar el estado y el motivo (deshacer/anular).
-- Nadie tiene DELETE: los registros contables no se borran.
-- La tabla cafeteria no se le da a la aplicación.
-- ---------------------------------------------------------------------
GRANT SELECT ON usuarios, categoria, producto, producto_adicion TO app_jjskaffee;
GRANT SELECT, INSERT ON venta, detalle_venta, egreso TO app_jjskaffee;
GRANT UPDATE (estado, motivo_anulacion) ON venta TO app_jjskaffee;
GRANT SELECT ON v_movimiento TO app_jjskaffee;

COMMIT;
