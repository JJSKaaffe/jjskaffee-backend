package co.edu.uco.jjskafee.ingresos.servicios.input.dto.response;

import co.edu.uco.jjskafee.ingresos.dominio.DetalleVenta;
import co.edu.uco.jjskafee.ingresos.dominio.Venta;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Respuesta de POST /api/ingresos. El idVenta lo usa el frontend para el botón "Deshacer". */
public record IngresoResponse(UUID idVenta, Instant fechaHora, String estado, BigDecimal total,
                              UUID registradoPor, List<ItemResponse> items) {

    public static IngresoResponse desde(Venta venta) {
        List<ItemResponse> items = venta.getDetalles().stream()
                .filter(d -> !d.esAdicion())
                .map(base -> new ItemResponse(
                        base.getProducto().getId(), base.getProducto().getNombre(), base.getCantidad(),
                        base.getPrecioUnitario(), base.subtotal(),
                        venta.getDetalles().stream()
                                .filter(d -> d.getDetalleBase() == base)
                                .map(IngresoResponse::adicion)
                                .toList()))
                .toList();
        return new IngresoResponse(venta.getId(), venta.getFechaHora(), venta.getEstado().name(),
                venta.total(), venta.getIdCajero(), items);
    }

    private static AdicionResponse adicion(DetalleVenta d) {
        return new AdicionResponse(d.getProducto().getId(), d.getProducto().getNombre(), d.getCantidad(),
                d.getPrecioUnitario(), d.subtotal());
    }
}
