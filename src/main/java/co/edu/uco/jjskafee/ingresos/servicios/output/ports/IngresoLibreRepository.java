package co.edu.uco.jjskafee.ingresos.servicios.output.ports;

import co.edu.uco.jjskafee.ingresos.dominio.IngresoLibre;

import java.util.Optional;
import java.util.UUID;

/** Puerto de salida: guardar y leer ingresos libres (siempre dentro de UnidadDeTrabajo). */
public interface IngresoLibreRepository {

    /**
     * Guarda el ingreso. Devuelve false si otro ingreso con la misma Idempotency-Key ya está guardado
     * en la cafetería (por ejemplo, dos reintentos al mismo tiempo); en ese caso no guarda nada.
     */
    boolean guardar(IngresoLibre ingreso);

    Optional<IngresoLibre> buscarPorId(UUID idIngreso);

    /** Ingreso de la cafetería activa registrado con esa Idempotency-Key. */
    Optional<IngresoLibre> buscarPorClaveIdempotencia(UUID claveIdempotencia);
}
