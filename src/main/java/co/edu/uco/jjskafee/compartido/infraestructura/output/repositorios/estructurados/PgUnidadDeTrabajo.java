package co.edu.uco.jjskafee.compartido.infraestructura.output.repositorios.estructurados;

import co.edu.uco.jjskafee.compartido.servicios.output.ports.UnidadDeTrabajo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Abre una transacción y fija la cafetería activa (o la identidad) para Row Level Security
 * (ADR-0002, ADR-0012, ADR-0016). set_config(..., true) dura solo hasta que termina la transacción,
 * así que una conexión del pool nunca queda "pegada" a una cafetería. Si la operación falla, no se guarda nada.
 *
 * app.current_cafeteria y app.current_uid nunca quedan fijadas a la vez: la misma sentencia que fija
 * una borra la otra. Así, aunque dos llamadas compartan una transacción (p. ej. en las pruebas), las
 * políticas de membresías nunca se suman a las de la cafetería.
 */
public class PgUnidadDeTrabajo implements UnidadDeTrabajo {

    private static final String CAFETERIA = "app.current_cafeteria";
    private static final String UID = "app.current_uid";

    private final TransactionTemplate transaccion;
    private final JdbcTemplate jdbc;

    public PgUnidadDeTrabajo(TransactionTemplate transaccion, JdbcTemplate jdbc) {
        this.transaccion = Objects.requireNonNull(transaccion);
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public <T> T ejecutar(UUID idCafeteria, Supplier<T> operacion) {
        Objects.requireNonNull(idCafeteria, "La cafetería es obligatoria para acceder a los datos");
        return ejecutarCon(CAFETERIA, idCafeteria.toString(), UID, operacion);
    }

    @Override
    public <T> T ejecutarConIdentidad(String firebaseUid, Supplier<T> operacion) {
        if (firebaseUid == null || firebaseUid.isBlank()) {
            throw new IllegalArgumentException("La identidad es obligatoria para consultar las membresías");
        }
        return ejecutarCon(UID, firebaseUid, CAFETERIA, operacion);
    }

    private <T> T ejecutarCon(String variable, String valor, String otra, Supplier<T> operacion) {
        return transaccion.execute(estado -> {
            jdbc.queryForList("SELECT set_config(?, ?, true), set_config(?, '', true)", variable, valor, otra);
            return operacion.get();
        });
    }
}
