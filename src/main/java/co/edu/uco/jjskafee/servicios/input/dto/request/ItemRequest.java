package co.edu.uco.jjskafee.servicios.input.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Un producto base de la venta con sus adiciones. */
public record ItemRequest(
        @NotNull(message = "El producto es obligatorio") UUID idProducto,
        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad debe ser mínimo 1")
        @Max(value = 99, message = "La cantidad máxima es 99") Integer cantidad,
        @Size(max = 49, message = "Un producto no puede tener más de 49 adiciones")
        List<@Valid AdicionRequest> adiciones
) {
    public List<AdicionRequest> adicionesOVacia() {
        return adiciones == null ? List.of() : adiciones;
    }
}
