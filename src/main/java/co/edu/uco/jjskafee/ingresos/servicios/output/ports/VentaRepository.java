package co.edu.uco.jjskafee.ingresos.servicios.output.ports;

import co.edu.uco.jjskafee.ingresos.dominio.Venta;

import java.util.Optional;
import java.util.UUID;

/** Puerto de salida: guardar y leer ventas. Lo implementa un adaptador (PostgreSQL). */
public interface VentaRepository {

    /**
     * Guarda la venta y todos sus detalles (tablas venta y detalle_venta).
     *
     * @return false si no se guardó porque la cafetería ya tiene otra venta con la misma
     *         clave de idempotencia (dos peticiones iguales al mismo tiempo); true si se guardó
     */
    boolean guardar(Venta venta);

    Optional<Venta> buscarPorId(UUID idVenta);

    /** La venta de la cafetería activa registrada con esa Idempotency-Key, si existe. */
    Optional<Venta> buscarPorClaveIdempotencia(UUID claveIdempotencia);

    /** Guarda el cambio de estado (por ejemplo, cuando se deshace). */
    void actualizarEstado(Venta venta);
}
