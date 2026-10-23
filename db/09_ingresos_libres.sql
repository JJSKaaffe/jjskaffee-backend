-- =====================================================================
-- JJSKaffee · 09 · Ingresos libres (monto + categoría de ingreso)
-- Ejecutar en pgAdmin conectado a la base "jjskaffee" con el usuario postgres,
-- después de 08_seed_multicafeteria.sql (y antes del seed 10_seed_categorias_ingreso.sql).
--
-- Migración, no seed: no inserta datos. Se corre una sola vez por base.
--
-- CAMBIO de decisión: antes "todo ingreso es una venta". Ahora un ingreso es una
-- venta (productos del menú, tabla venta) o un ingreso libre (monto + categoría
-- de tipo INGRESO, tabla ingreso_libre). El ingreso libre solo lo registra el
-- ADMINISTRADOR; esa regla, la del tipo de categoría y la de categoría no
-- archivada las valida el dominio (IngresoLibre), como en egreso.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- Tabla. Mismas garantías que egreso, más la idempotencia de venta (db/05):
--   * FK compuestas con id_cafeteria: nunca apunta a una categoría ni a un
--     usuario de otra cafetería (las FK no pasan por RLS).
--   * monto > 0 en numeric(12,2): máximo 10 dígitos enteros y 2 decimales.
--   * Borrado lógico: se anula con motivo, nunca se borra.
--   * clave_idempotencia + hash_solicitud: un reintento con la misma
--     Idempotency-Key devuelve el ingreso ya registrado (UNIQUE por cafetería).
-- ---------------------------------------------------------------------
CREATE TABLE ingreso_libre (
    id                  uuid          PRIMARY KEY,
    id_cafeteria        uuid          NOT NULL,
    id_categoria        uuid          NOT NULL,
    monto               numeric(12,2) NOT NULL CHECK (monto > 0),
    descripcion         varchar(200)  NOT NULL,
    comprobante         varchar(50),
    registrado_por      uuid          NOT NULL,
    fecha_hora          timestamptz   NOT NULL,
    anulado             boolean       NOT NULL DEFAULT false,
    motivo_anulacion    varchar(200),
    clave_idempotencia  uuid,
    hash_solicitud      char(64),
    UNIQUE (id_cafeteria, id),
    FOREIGN KEY (id_cafeteria, id_categoria)   REFERENCES categoria (id_cafeteria, id),
    FOREIGN KEY (id_cafeteria, registrado_por) REFERENCES usuarios (id_cafeteria, id),
    CHECK (NOT anulado OR motivo_anulacion IS NOT NULL),
    CONSTRAINT uq_ingreso_libre_cafeteria_clave_idempotencia UNIQUE (id_cafeteria, clave_idempotencia),
    CONSTRAINT ck_ingreso_libre_clave_con_hash CHECK ((clave_idempotencia IS NULL) = (hash_solicitud IS NULL))
);

-- Índices para filtros por cafetería, fecha, categoría y usuario (ADR-0004)
CREATE INDEX ix_ingreso_libre_cafeteria_fecha         ON ingreso_libre (id_cafeteria, fecha_hora DESC);
CREATE INDEX ix_ingreso_libre_cafeteria_categoria     ON ingreso_libre (id_cafeteria, id_categoria, fecha_hora DESC);
CREATE INDEX ix_ingreso_libre_cafeteria_usuario_fecha ON ingreso_libre (id_cafeteria, registrado_por, fecha_hora DESC);

-- ---------------------------------------------------------------------
-- Row Level Security: ENABLE + FORCE y la misma política que las demás
-- tablas (02_esquema.sql): solo filas de la cafetería activa, para leer y escribir.
-- No tiene política por app.current_uid: con la identidad fijada no se ve nada.
-- ---------------------------------------------------------------------
ALTER TABLE ingreso_libre ENABLE ROW LEVEL SECURITY;
ALTER TABLE ingreso_libre FORCE ROW LEVEL SECURITY;
CREATE POLICY aislamiento_cafeteria ON ingreso_libre
    USING (id_cafeteria = app_cafeteria_actual())
    WITH CHECK (id_cafeteria = app_cafeteria_actual());

-- ---------------------------------------------------------------------
-- Vista de movimientos: el ingreso libre sale como INGRESO, con su categoría.
-- Mismas columnas y tipos que en 02_esquema.sql (por eso sirve OR REPLACE y se
-- conservan los permisos). security_invoker = true: la vista se evalúa con el
-- RLS de quien consulta; sin esto correría como postgres y se saltaría RLS.
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_movimiento WITH (security_invoker = true) AS
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
WHERE NOT e.anulado
UNION ALL
SELECT l.id,
       l.id_cafeteria,
       'INGRESO'::varchar(7),
       l.fecha_hora,
       l.monto,
       l.descripcion,
       l.id_categoria,
       c.nombre,
       l.registrado_por,
       u.nombre
FROM ingreso_libre l
JOIN categoria c ON c.id_cafeteria = l.id_cafeteria AND c.id = l.id_categoria
JOIN usuarios u  ON u.id_cafeteria = l.id_cafeteria AND u.id = l.registrado_por
WHERE NOT l.anulado;

-- ---------------------------------------------------------------------
-- Permisos mínimos: leer e insertar, igual que egreso. Sin UPDATE ni DELETE:
-- la anulación con motivo llegará con su propio caso de uso y su propio GRANT.
-- ---------------------------------------------------------------------
GRANT SELECT, INSERT ON ingreso_libre TO app_jjskaffee;

COMMIT;
