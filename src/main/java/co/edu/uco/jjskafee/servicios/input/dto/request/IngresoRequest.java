package co.edu.uco.jjskafee.servicios.input.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Lo que manda el frontend para registrar una venta (POST /api/ingresos).
 * Solo trae ids y cantidades: NO trae precios, total, cafetería, usuario ni fecha.
 */
public record IngresoRequest(
        @NotEmpty(message = "La venta debe tener al menos un producto")
        List<@Valid ItemRequest> items
) {
}
