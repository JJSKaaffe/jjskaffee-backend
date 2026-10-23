package co.edu.uco.jjskafee.egresos.servicios.input.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lo que manda el frontend para registrar un egreso (POST /api/egresos).
 * No trae idCafeteria, registradoPor ni fechaHora: los pone el servidor (ADR-0001, ADR-0007).
 */
public record EgresoRequest(
        @NotNull(message = "Elige una categoría") UUID idCategoria,
        @NotNull(message = "El monto es obligatorio") @Positive(message = "Debe ser mayor que 0") BigDecimal monto,
        @NotBlank(message = "Escribe una descripción") @Size(max = 200, message = "Máximo 200 caracteres") String descripcion,
        @Size(max = 50, message = "Máximo 50 caracteres") String comprobante
) {
}
