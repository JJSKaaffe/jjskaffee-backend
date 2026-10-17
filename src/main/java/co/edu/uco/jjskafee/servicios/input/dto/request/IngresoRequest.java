package co.edu.uco.jjskafee.servicios.input.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Lo que manda el frontend para registrar una venta (POST /api/ingresos).
 * Solo trae ids y cantidades: NO trae precios, total, cafetería, usuario ni fecha.
 * Los límites de tamaño evitan procesar cuerpos enormes; la regla de 50 líneas
 * (productos + adiciones) la valida Venta.
 */
public record IngresoRequest(
        @NotEmpty(message = "La venta debe tener al menos un producto")
        @Size(max = 50, message = "La venta no puede tener más de 50 productos")
        List<@Valid ItemRequest> items
) {
}
