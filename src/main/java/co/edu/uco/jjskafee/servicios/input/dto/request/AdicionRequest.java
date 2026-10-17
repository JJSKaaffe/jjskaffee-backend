package co.edu.uco.jjskafee.servicios.input.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Una adición dentro de un ítem. Si no se manda cantidad, se toma 1. */
public record AdicionRequest(
        @NotNull(message = "La adición es obligatoria") UUID idProducto,
        @Min(value = 1, message = "La cantidad debe ser mínimo 1")
        @Max(value = 99, message = "La cantidad máxima es 99") Integer cantidad
) {
    public int cantidadOUno() {
        return cantidad == null ? 1 : cantidad;
    }
}
