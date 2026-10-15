package co.edu.uco.jjskafee.ingresos.infraestructura.web;

import co.edu.uco.jjskafee.ingresos.aplicacion.comandos.RegistrarVentaComando;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Cuerpo de POST /api/ingresos. Solo ids y cantidades: NO trae precios, total,
 * cafetería, usuario ni fecha. Los mensajes de validación son claves de i18n/mensajes.properties.
 */
public record IngresoRequest(
        @NotEmpty(message = "{validacion.ingreso.items.obligatorio}")
        List<@Valid ItemRequest> items
) {

    public RegistrarVentaComando aComando() {
        return new RegistrarVentaComando(items.stream().map(ItemRequest::aLinea).toList());
    }

    /** Un producto base de la venta con sus adiciones. */
    public record ItemRequest(
            @NotNull(message = "{validacion.producto.obligatorio}") UUID idProducto,
            @NotNull(message = "{validacion.cantidad.obligatoria}")
            @Min(value = 1, message = "{validacion.cantidad.minima}") Integer cantidad,
            List<@Valid AdicionRequest> adiciones
    ) {
        RegistrarVentaComando.Linea aLinea() {
            List<AdicionRequest> lista = adiciones == null ? List.of() : adiciones;
            return new RegistrarVentaComando.Linea(idProducto, cantidad,
                    lista.stream().map(AdicionRequest::aAdicion).toList());
        }
    }

    /** Una adición dentro de un ítem. Si no se manda cantidad, se toma 1. */
    public record AdicionRequest(
            @NotNull(message = "{validacion.adicion.obligatoria}") UUID idProducto,
            @Min(value = 1, message = "{validacion.cantidad.minima}") Integer cantidad
    ) {
        RegistrarVentaComando.Adicion aAdicion() {
            return new RegistrarVentaComando.Adicion(idProducto, cantidad == null ? 1 : cantidad);
        }
    }
}
