package co.edu.uco.jjskafee.ingresos.infraestructura.output.repositorios.estructurados;

import co.edu.uco.jjskafee.ingresos.servicios.output.ports.CafeteriaRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Objects;
import java.util.UUID;

/**
 * Lee si la cafetería está activa. La app solo tiene SELECT (id, activo) sobre cafeteria
 * (db/05) y RLS deja ver únicamente la fila de la cafetería activa de la transacción.
 */
public class PgCafeteriaRepository implements CafeteriaRepository {

    private final JdbcTemplate jdbc;

    public PgCafeteriaRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public boolean estaActiva(UUID idCafeteria) {
        return jdbc.queryForList("SELECT activo FROM cafeteria WHERE id = ?", Boolean.class, idCafeteria)
                .stream().findFirst().orElse(false);
    }
}
