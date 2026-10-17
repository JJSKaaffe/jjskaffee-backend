package co.edu.uco.jjskafee.servicios.input.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record AdicionResponse(UUID idProducto, String nombre, int cantidad,
                              BigDecimal precioUnitario, BigDecimal subtotal) {
}
