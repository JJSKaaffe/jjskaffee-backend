package co.edu.uco.jjskafee.movimientos.infraestructura.persistencia;

import co.edu.uco.jjskafee.movimientos.aplicacion.puertos.salida.RepositorioMovimientos;
import co.edu.uco.jjskafee.movimientos.dominio.FiltroMovimientos;
import co.edu.uco.jjskafee.movimientos.dominio.Movimiento;
import co.edu.uco.jjskafee.movimientos.dominio.ResumenMovimientos;
import co.edu.uco.jjskafee.movimientos.dominio.TipoMovimiento;
import co.edu.uco.jjskafee.movimientos.dominio.Totales;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Consulta sobre la vista v_movimiento (security_invoker = true → respeta el RLS).
 * Los totales se calculan con SUM ... FILTER en la BD y la lista va paginada (ADR-0004, SPIKE-06);
 * los índices (id_cafeteria, fecha_hora) de venta y egreso hacen rápido el filtro por fechas.
 *
 * Es un modelo de solo lectura: une la vista con categoria y usuario únicamente para mostrar nombres.
 * El WHERE se arma con fragmentos fijos y parámetros "?" (nunca se concatena lo que manda el usuario).
 */
public class PgRepositorioMovimientos implements RepositorioMovimientos {

    private static final String SQL_LISTA = """
            SELECT m.id_movimiento, m.tipo, m.fecha_hora, m.monto, m.descripcion,
                   c.nombre AS categoria,
                   u.nombre || ' ' || u.apellido AS registrado_por
              FROM v_movimiento m
              LEFT JOIN categoria c ON c.id_cafeteria = m.id_cafeteria AND c.id_categoria = m.id_categoria
              LEFT JOIN usuario   u ON u.id_cafeteria = m.id_cafeteria AND u.id_usuario  = m.registrado_por
            """;

    private static final String ORDEN_Y_PAGINA = """

             ORDER BY m.fecha_hora DESC, m.id_movimiento
             LIMIT ? OFFSET ?
            """;

    private static final String SQL_RESUMEN = """
            SELECT COALESCE(SUM(m.monto) FILTER (WHERE m.tipo = 'INGRESO'), 0) AS ingresos,
                   COALESCE(SUM(m.monto) FILTER (WHERE m.tipo = 'EGRESO'), 0)  AS egresos,
                   COUNT(*) AS cantidad
              FROM v_movimiento m
            """;

    private final JdbcTemplate jdbc;

    public PgRepositorioMovimientos(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public List<Movimiento> buscar(UUID idCafeteria, FiltroMovimientos filtro, UUID soloDelUsuario) {
        Condiciones condiciones = Condiciones.de(idCafeteria, filtro, soloDelUsuario);
        List<Object> parametros = new ArrayList<>(condiciones.parametros());
        parametros.add(filtro.tamano());
        parametros.add(filtro.desplazamiento());
        return jdbc.query(SQL_LISTA + condiciones.where() + ORDEN_Y_PAGINA, (fila, i) -> new Movimiento(
                fila.getObject("id_movimiento", UUID.class),
                TipoMovimiento.valueOf(fila.getString("tipo")),
                fila.getObject("fecha_hora", OffsetDateTime.class).toInstant(),
                fila.getBigDecimal("monto"),
                fila.getString("descripcion"),
                fila.getString("categoria"),
                fila.getString("registrado_por")), parametros.toArray());
    }

    @Override
    public ResumenMovimientos resumir(UUID idCafeteria, FiltroMovimientos filtro, UUID soloDelUsuario) {
        Condiciones condiciones = Condiciones.de(idCafeteria, filtro, soloDelUsuario);
        return jdbc.queryForObject(SQL_RESUMEN + condiciones.where(), (fila, i) -> new ResumenMovimientos(
                new Totales(fila.getBigDecimal("ingresos"), fila.getBigDecimal("egresos")),
                fila.getLong("cantidad")), condiciones.parametros().toArray());
    }

    /** WHERE común a la lista y al resumen, para que los totales siempre cuadren con la lista. */
    private record Condiciones(String where, List<Object> parametros) {

        static Condiciones de(UUID idCafeteria, FiltroMovimientos filtro, UUID soloDelUsuario) {
            StringBuilder where = new StringBuilder(" WHERE m.id_cafeteria = ?");
            List<Object> parametros = new ArrayList<>();
            parametros.add(idCafeteria);
            if (filtro.inicioUtc() != null) {
                where.append(" AND m.fecha_hora >= ?");
                parametros.add(filtro.inicioUtc().atOffset(ZoneOffset.UTC));
            }
            if (filtro.finUtcExclusivo() != null) {
                where.append(" AND m.fecha_hora < ?");
                parametros.add(filtro.finUtcExclusivo().atOffset(ZoneOffset.UTC));
            }
            if (filtro.tipo() != null) {
                where.append(" AND m.tipo = ?");
                parametros.add(filtro.tipo().name());
            }
            if (filtro.idCategoria() != null) {
                where.append(" AND m.id_categoria = ?");
                parametros.add(filtro.idCategoria());
            }
            if (soloDelUsuario != null) {
                where.append(" AND m.registrado_por = ?");
                parametros.add(soloDelUsuario);
            }
            return new Condiciones(where.toString(), List.copyOf(parametros));
        }
    }
}
