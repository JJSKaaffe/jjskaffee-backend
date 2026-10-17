package co.edu.uco.jjskafee.infraestructura.output.repositorios.estructurados;

import co.edu.uco.jjskafee.dominio.Egreso;
import co.edu.uco.jjskafee.servicios.output.ports.EgresoRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Objects;

import static co.edu.uco.jjskafee.infraestructura.output.repositorios.estructurados.ConversionesSql.aTimestamp;

/** Guarda egresos en la tabla egreso. La política RLS rechaza un egreso de otra cafetería. */
public class PgEgresoRepository implements EgresoRepository {

    private final JdbcTemplate jdbc;

    public PgEgresoRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public void guardar(Egreso egreso) {
        jdbc.update("""
                        INSERT INTO egreso (id, id_cafeteria, id_categoria, monto, descripcion, comprobante,
                                            registrado_por, fecha_hora)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                egreso.getId(), egreso.getIdCafeteria(), egreso.getCategoria().getId(), egreso.getMonto(),
                egreso.getDescripcion(), egreso.getComprobante(), egreso.getRegistradoPor(),
                aTimestamp(egreso.getFechaHora()));
    }
}
