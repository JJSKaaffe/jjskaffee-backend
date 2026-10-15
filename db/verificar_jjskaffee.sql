-- =====================================================================
-- JJSKaffee · Base de datos · 04_pruebas.sql
-- Pruebas de aislamiento y reglas, versión para pgAdmin.
-- Se ejecuta DESPUÉS de jjskaffee_completo.sql, en el Query Tool de la base jjskaffee.
-- Se hace pasar por app_jjskaffee (el usuario limitado del backend).
-- Resultado: pestaña "Messages" con "OK 1" ... "OK 9". Si algo falla, sale un ERROR.
-- =====================================================================
SET ROLE app_jjskaffee;
SET search_path TO jjskaffee, public;

-- 1. Sin cafetería fijada no se ve nada
SELECT set_config('app.current_cafeteria', '', false);
DO $$ BEGIN
  IF (SELECT count(*) FROM egreso) <> 0 THEN RAISE EXCEPTION 'FALLA 1: se ven egresos sin cafetería'; END IF;
  RAISE NOTICE 'OK 1: sin cafetería fijada no se ve ningún dato';
END $$;

-- 2. Café Demo solo ve lo suyo
SELECT set_config('app.current_cafeteria', '11111111-1111-1111-1111-111111111111', false);
DO $$ BEGIN
  IF (SELECT count(*) FROM egreso) <> 2 THEN RAISE EXCEPTION 'FALLA 2: Café Demo debería ver 2 egresos'; END IF;
  IF EXISTS (SELECT 1 FROM egreso WHERE descripcion LIKE '%Vecino%') THEN RAISE EXCEPTION 'FALLA 2: fuga de datos'; END IF;
  RAISE NOTICE 'OK 2: Café Demo ve solo sus 2 egresos (0%% de fuga)';
END $$;

-- 3. Totales desde la vista de movimientos
DO $$
DECLARE ing numeric; egr numeric;
BEGIN
  SELECT coalesce(sum(monto) FILTER (WHERE tipo='INGRESO'),0),
         coalesce(sum(monto) FILTER (WHERE tipo='EGRESO'),0) INTO ing, egr FROM v_movimiento;
  IF ing <> 26500 OR egr <> 165000 THEN RAISE EXCEPTION 'FALLA 3: ingresos % egresos %', ing, egr; END IF;
  RAISE NOTICE 'OK 3: ingresos = %, egresos = %, balance = %', ing, egr, ing - egr;
END $$;

-- 4. Registrar un egreso válido (lo que hará POST /egresos)
INSERT INTO egreso (id_cafeteria, id_categoria, monto, descripcion, comprobante, registrado_por)
VALUES ('11111111-1111-1111-1111-111111111111', 'c0000000-0000-0000-0000-000000000011',
        30000, 'Prueba: azúcar y vasos', 'PRB-1', 'a0000000-0000-0000-0000-000000000001');
DO $$ BEGIN RAISE NOTICE 'OK 4: egreso válido registrado'; END $$;

-- 5. No se puede escribir en otra cafetería (WITH CHECK de RLS)
DO $$ BEGIN
  BEGIN
    INSERT INTO egreso (id_cafeteria, id_categoria, monto, descripcion, registrado_por)
    VALUES ('22222222-2222-2222-2222-222222222222', 'c0000000-0000-0000-0000-000000000021',
            1, 'intento de escritura cruzada', 'a0000000-0000-0000-0000-000000000003');
    RAISE EXCEPTION 'FALLA 5: se pudo escribir en otra cafetería';
  EXCEPTION WHEN insufficient_privilege OR check_violation OR foreign_key_violation THEN
    -- Lo bloquea la primera barrera que lo detecte: RLS, la validación de
    -- categoría (no ve la categoría ajena) o la FK compuesta por cafetería.
    RAISE NOTICE 'OK 5: escritura en otra cafetería bloqueada (%)', SQLSTATE;
  END;
END $$;

-- 6. Monto negativo rechazado
DO $$ BEGIN
  BEGIN
    INSERT INTO egreso (id_cafeteria, id_categoria, monto, descripcion, registrado_por)
    VALUES ('11111111-1111-1111-1111-111111111111', 'c0000000-0000-0000-0000-000000000011',
            -5, 'monto negativo', 'a0000000-0000-0000-0000-000000000001');
    RAISE EXCEPTION 'FALLA 6: se aceptó un monto negativo';
  EXCEPTION WHEN check_violation THEN
    RAISE NOTICE 'OK 6: monto negativo rechazado';
  END;
END $$;

-- 7. Categoría de menú o archivada no sirve para egresos
DO $$ BEGIN
  BEGIN
    INSERT INTO egreso (id_cafeteria, id_categoria, monto, descripcion, registrado_por)
    VALUES ('11111111-1111-1111-1111-111111111111', 'c0000000-0000-0000-0000-000000000015',
            1000, 'categoría archivada', 'a0000000-0000-0000-0000-000000000001');
    RAISE EXCEPTION 'FALLA 7: se aceptó una categoría archivada';
  EXCEPTION WHEN check_violation THEN
    RAISE NOTICE 'OK 7: categoría archivada o de otro tipo rechazada';
  END;
END $$;

-- 8. Venta completa en una transacción (lo que hará POST /ingresos)
BEGIN;
  INSERT INTO venta (id_venta, id_cafeteria, id_cajero, total)
  VALUES ('d0000000-0000-0000-0000-0000000000aa', '11111111-1111-1111-1111-111111111111',
          'a0000000-0000-0000-0000-000000000002', 8500);
  INSERT INTO detalle_venta (id_cafeteria, id_venta, id_producto, cantidad, precio_unitario) VALUES
  ('11111111-1111-1111-1111-111111111111', 'd0000000-0000-0000-0000-0000000000aa', 'b0000000-0000-0000-0000-000000000003', 1, 7000),
  ('11111111-1111-1111-1111-111111111111', 'd0000000-0000-0000-0000-0000000000aa', 'b0000000-0000-0000-0000-000000000011', 1, 1500);
COMMIT;
DO $$ BEGIN
  IF (SELECT sum(subtotal) FROM detalle_venta WHERE id_venta = 'd0000000-0000-0000-0000-0000000000aa') <> 8500
  THEN RAISE EXCEPTION 'FALLA 8: el detalle no suma el total'; END IF;
  RAISE NOTICE 'OK 8: venta con adición registrada y el detalle suma el total (8500)';
END $$;

-- 9. Los movimientos no se pueden borrar (sin GRANT DELETE)
DO $$ BEGIN
  BEGIN
    DELETE FROM egreso;
    RAISE EXCEPTION 'FALLA 9: se pudo borrar un egreso';
  EXCEPTION WHEN insufficient_privilege THEN
    RAISE NOTICE 'OK 9: borrar movimientos no está permitido (borrado lógico)';
  END;
END $$;

-- Limpieza de los datos creados por las pruebas no es posible (sin DELETE):
-- vuelve a cargar 01, 02 y 03 si quieres la base limpia.


RESET ROLE;
