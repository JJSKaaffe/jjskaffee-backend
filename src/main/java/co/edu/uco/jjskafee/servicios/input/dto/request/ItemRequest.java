package co.edu.uco.jjskafee.servicios.input.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Un producto base de la venta con sus adiciones. */
public record ItemRequest(
        @NotNull(message = "El producto es obligatorio") UUID idProducto,
        @NotNull @Min(value = 1, message = "La cantidad debe ser mínimo 1") Integer cantidad,
        List<@Valid AdicionRequest> adiciones
) {
    public List<AdicionRequest> adicionesOVacia() {
        return adiciones == null ? List.of() : adiciones;
    }
}
