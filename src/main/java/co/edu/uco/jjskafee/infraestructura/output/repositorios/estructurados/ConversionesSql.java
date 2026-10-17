package co.edu.uco.jjskafee.infraestructura.output.repositorios.estructurados;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Conversión entre Instant (dominio) y timestamptz (PostgreSQL). Siempre en UTC. */
final class ConversionesSql {

    private ConversionesSql() { }

    static OffsetDateTime aTimestamp(Instant instante) {
        return instante == null ? null : instante.atOffset(ZoneOffset.UTC);
    }

    static Instant leerInstante(ResultSet rs, String columna) throws SQLException {
        OffsetDateTime valor = rs.getObject(columna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }
}
