package co.edu.uco.jjskafee.ingresos.aplicacion.puertos.salida;

import co.edu.uco.jjskafee.ingresos.dominio.Venta;

import java.util.Optional;
import java.util.UUID;

/** Tablas venta y detalle_venta (dueño: módulo ingresos). */
public interface RepositorioVentas {

    /** Guarda la venta y todas sus líneas (dentro de la transacción abierta). */
    void guardar(Venta venta);

    /** Encabezado de la venta, sin detalles, para anularla. */
    Optional<Venta> buscarParaAnular(UUID idCafeteria, UUID idVenta);

    /** Guarda estado, motivo, quién anuló y cuándo. */
    void registrarAnulacion(Venta venta);
}
