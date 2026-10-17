package co.edu.uco.jjskafee.servicios.input.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ItemResponse(UUID idProducto, String nombre, int cantidad, BigDecimal precioUnitario,
                           BigDecimal subtotal, List<AdicionResponse> adiciones) {
}
