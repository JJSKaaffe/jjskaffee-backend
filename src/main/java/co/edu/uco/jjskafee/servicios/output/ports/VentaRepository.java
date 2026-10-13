package co.edu.uco.jjskafee.servicios.output.ports;

import co.edu.uco.jjskafee.dominio.Venta;

import java.util.Optional;
import java.util.UUID;

/** Puerto de salida: guardar y leer ventas. Lo implementa un adaptador (PostgreSQL). */
public interface VentaRepository {

    /** Guarda la venta y todos sus detalles (tablas venta y detalle_venta). */
    void guardar(Venta venta);

    Optional<Venta> buscarPorId(UUID idVenta);

    /** Guarda el cambio de estado (por ejemplo, cuando se deshace). */
    void actualizarEstado(Venta venta);
}
