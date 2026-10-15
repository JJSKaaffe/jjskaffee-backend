package co.edu.uco.jjskafee.ingresos.infraestructura.web;

import co.edu.uco.jjskafee.ingresos.dominio.DetalleVenta;
import co.edu.uco.jjskafee.ingresos.dominio.Venta;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta de POST /api/ingresos y de deshacer. El idVenta lo usa el frontend
 * para el botón "Deshacer"; deshacerHasta le dice hasta cuándo mostrarlo.
 */
public record IngresoResponse(UUID idVenta, Instant fechaHora, String estado, BigDecimal total,
                              UUID registradoPor, Instant deshacerHasta, List<ItemResponse> items) {

    public static IngresoResponse desde(Venta venta) {
        List<ItemResponse> items = venta.getDetalles().stream()
                .filter(d -> !d.esAdicion())
                .map(base -> new ItemResponse(
                        base.getProducto().id(), base.getProducto().nombre(), base.getCantidad(),
                        base.getPrecioUnitario(), base.subtotal(),
                        venta.getDetalles().stream()
                                .filter(d -> d.getDetalleBase() == base)
                                .map(IngresoResponse::adicion)
                                .toList()))
                .toList();
        return new IngresoResponse(venta.getId(), venta.getFechaHora(), venta.getEstado().name(), venta.total(),
                venta.getIdCajero(), venta.getFechaHora().plus(Venta.TIEMPO_PARA_DESHACER), items);
    }

    private static AdicionResponse adicion(DetalleVenta d) {
        return new AdicionResponse(d.getProducto().id(), d.getProducto().nombre(), d.getCantidad(),
                d.getPrecioUnitario(), d.subtotal());
    }

    public record ItemResponse(UUID idProducto, String nombre, int cantidad, BigDecimal precioUnitario,
                               BigDecimal subtotal, List<AdicionResponse> adiciones) {
    }

    public record AdicionResponse(UUID idProducto, String nombre, int cantidad, BigDecimal precioUnitario,
                                  BigDecimal subtotal) {
    }
}
