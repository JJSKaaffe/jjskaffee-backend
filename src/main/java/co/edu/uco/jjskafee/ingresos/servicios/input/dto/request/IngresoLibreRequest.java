package co.edu.uco.jjskafee.ingresos.servicios.input.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lo que manda el frontend para registrar un ingreso libre (POST /api/ingresos/libres): monto + categoría
 * de ingreso. No trae idCafeteria, registradoPor ni fechaHora: los pone el servidor (ADR-0007, ADR-0016).
 * El monto no se redondea: con más de 2 decimales o más de 10 dígitos enteros se rechaza (numeric(12,2)).
 */
public record IngresoLibreRequest(
        @NotNull(message = "Elige una categoría") UUID idCategoria,
        @NotNull(message = "El monto es obligatorio")
        @Positive(message = "Debe ser mayor que 0")
        @Digits(integer = 10, fraction = 2, message = "Máximo 10 dígitos enteros y 2 decimales")
        BigDecimal monto,
        @NotBlank(message = "Escribe una descripción") @Size(max = 200, message = "Máximo 200 caracteres") String descripcion,
        @Size(max = 50, message = "Máximo 50 caracteres") String comprobante
) {
}
