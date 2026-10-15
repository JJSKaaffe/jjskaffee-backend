package co.edu.uco.jjskafee.compartido.infraestructura.persistencia;

import co.edu.uco.jjskafee.compartido.aplicacion.puertos.UnidadDeTrabajo;
import co.edu.uco.jjskafee.compartido.dominio.mensajes.MensajesCompartidos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Transacción + aislamiento por cafetería (ADR-0001, ADR-0002, ADR-0012, SPIKE-03).
 *
 * En cada transacción ejecuta:  SELECT set_config('app.current_cafeteria', ?, true)
 * El "true" hace que el valor dure solo hasta el COMMIT/ROLLBACK, así una conexión del pool
 * nunca queda "pegada" a otra cafetería. Las políticas RLS de la base usan ese valor.
 *
 * Si un módulo llama a otro dentro de la misma operación (ingresos → catálogo), la segunda
 * llamada se une a la transacción abierta; mezclar dos cafeterías en una transacción está prohibido.
 */
public class PgUnidadDeTrabajo implements UnidadDeTrabajo {

    private static final String RECURSO_CAFETERIA = PgUnidadDeTrabajo.class.getName() + ".cafeteria";

    private final TransactionTemplate transaccion;
    private final JdbcTemplate jdbc;

    public PgUnidadDeTrabajo(TransactionTemplate transaccion, JdbcTemplate jdbc) {
        this.transaccion = Objects.requireNonNull(transaccion);
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public <T> T ejecutar(UUID idCafeteria, Supplier<T> operacion) {
        Objects.requireNonNull(idCafeteria);
        Objects.requireNonNull(operacion);
        return transaccion.execute(estado -> {
            UUID cafeteriaActiva = (UUID) TransactionSynchronizationManager.getResource(RECURSO_CAFETERIA);
            if (cafeteriaActiva == null) {
                activarCafeteria(idCafeteria);
            } else if (!cafeteriaActiva.equals(idCafeteria)) {
                throw new IllegalStateException(MensajesCompartidos.CAFETERIA_DISTINTA_EN_TRANSACCION.clave());
            }
            return operacion.get();
        });
    }

    private void activarCafeteria(UUID idCafeteria) {
        jdbc.queryForObject("SELECT set_config('app.current_cafeteria', ?, true)", String.class,
                idCafeteria.toString());
        TransactionSynchronizationManager.bindResource(RECURSO_CAFETERIA, idCafeteria);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int estado) {
                TransactionSynchronizationManager.unbindResourceIfPossible(RECURSO_CAFETERIA);
            }
        });
    }
}
