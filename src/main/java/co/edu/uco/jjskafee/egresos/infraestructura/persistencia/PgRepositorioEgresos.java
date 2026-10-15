package co.edu.uco.jjskafee.egresos.infraestructura.persistencia;

import co.edu.uco.jjskafee.egresos.aplicacion.puertos.salida.RepositorioEgresos;
import co.edu.uco.jjskafee.egresos.dominio.Egreso;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Tabla egreso con JDBC. La BD vuelve a validar la categoría (trigger trg_egreso_categoria),
 * el monto (CHECK) y la cafetería (RLS WITH CHECK): si algo se escapara del dominio, no entra.
 */
public class PgRepositorioEgresos implements RepositorioEgresos {

    private static final String SQL_INSERTAR = """
            INSERT INTO egreso (id_egreso, id_cafeteria, id_categoria, monto, descripcion, comprobante,
                                registrado_por, fecha_hora)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;

    public PgRepositorioEgresos(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public void guardar(Egreso egreso) {
        jdbc.update(SQL_INSERTAR, egreso.getId(), egreso.getIdCafeteria(), egreso.getCategoria().id(),
                egreso.getMonto(), egreso.getDescripcion(), egreso.getComprobante(), egreso.getRegistradoPor(),
                egreso.getFechaHora().atOffset(ZoneOffset.UTC));
    }
}
