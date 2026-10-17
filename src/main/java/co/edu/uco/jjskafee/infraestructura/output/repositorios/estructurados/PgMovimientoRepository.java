package co.edu.uco.jjskafee.infraestructura.output.repositorios.estructurados;

import co.edu.uco.jjskafee.dominio.FiltroMovimientos;
import co.edu.uco.jjskafee.dominio.Movimiento;
import co.edu.uco.jjskafee.dominio.ResultadoMovimientos;
import co.edu.uco.jjskafee.dominio.enums.TipoMovimiento;
import co.edu.uco.jjskafee.servicios.output.ports.MovimientoRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static co.edu.uco.jjskafee.infraestructura.output.repositorios.estructurados.ConversionesSql.aTimestamp;
import static co.edu.uco.jjskafee.infraestructura.output.repositorios.estructurados.ConversionesSql.leerInstante;

/**
 * Consulta la vista v_movimiento (ventas confirmadas + egresos no anulados).
 * Hace dos consultas: los totales con SUM sobre todo el filtro y la página pedida (ADR-0004).
 */
public class PgMovimientoRepository implements MovimientoRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ZoneId zonaHoraria;

    /** @param zonaHoraria zona en la que se interpretan las fechas del filtro (America/Bogota) */
    public PgMovimientoRepository(NamedParameterJdbcTemplate jdbc, ZoneId zonaHoraria) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.zonaHoraria = Objects.requireNonNull(zonaHoraria);
    }

    @Override
    public ResultadoMovimientos buscar(FiltroMovimientos filtro) {
        MapSqlParameterSource parametros = new MapSqlParameterSource();
        String where = condiciones(filtro, parametros);

        Totales totales = jdbc.queryForObject("""
                        SELECT COALESCE(SUM(monto) FILTER (WHERE tipo = 'INGRESO'), 0) AS ingresos,
                               COALESCE(SUM(monto) FILTER (WHERE tipo = 'EGRESO'), 0)  AS egresos,
                               COUNT(*) AS cantidad
                        FROM v_movimiento""" + where,
                parametros,
                (rs, n) -> new Totales(rs.getBigDecimal("ingresos"), rs.getBigDecimal("egresos"), rs.getLong("cantidad")));

        parametros.addValue("tamano", filtro.tamano());
        parametros.addValue("desplazamiento", filtro.desplazamiento());
        List<Movimiento> pagina = jdbc.query("""
                        SELECT id, tipo, fecha_hora, monto, descripcion, categoria, registrado_por
                        FROM v_movimiento""" + where + """
                         ORDER BY fecha_hora DESC, id
                        LIMIT :tamano OFFSET :desplazamiento""",
                parametros,
                (rs, n) -> new Movimiento(
                        rs.getObject("id", UUID.class),
                        TipoMovimiento.valueOf(rs.getString("tipo")),
                        leerInstante(rs, "fecha_hora"),
                        rs.getBigDecimal("monto"),
                        rs.getString("descripcion"),
                        rs.getString("categoria"),
                        rs.getString("registrado_por")));

        return new ResultadoMovimientos(pagina, totales.ingresos(), totales.egresos(), totales.cantidad());
    }

    /** Arma el WHERE solo con los filtros presentes, para que PostgreSQL use los índices. */
    private String condiciones(FiltroMovimientos filtro, MapSqlParameterSource parametros) {
        List<String> condiciones = new ArrayList<>();
        if (filtro.desde() != null) {
            condiciones.add("fecha_hora >= :desde");
            parametros.addValue("desde", aTimestamp(filtro.desde().atStartOfDay(zonaHoraria).toInstant()));
        }
        if (filtro.hasta() != null) {
            // 'hasta' incluye todo el día: hasta antes de la medianoche del día siguiente
            condiciones.add("fecha_hora < :hasta");
            parametros.addValue("hasta", aTimestamp(filtro.hasta().plusDays(1).atStartOfDay(zonaHoraria).toInstant()));
        }
        if (filtro.tipo() != null) {
            condiciones.add("tipo = :tipo");
            parametros.addValue("tipo", filtro.tipo().name());
        }
        if (filtro.idCategoria() != null) {
            condiciones.add("id_categoria = :idCategoria");
            parametros.addValue("idCategoria", filtro.idCategoria());
        }
        if (filtro.soloDelUsuario() != null) {
            condiciones.add("id_registrado_por = :usuario");
            parametros.addValue("usuario", filtro.soloDelUsuario());
        }
        return condiciones.isEmpty() ? "" : " WHERE " + String.join(" AND ", condiciones);
    }

    private record Totales(BigDecimal ingresos, BigDecimal egresos, long cantidad) { }
}
