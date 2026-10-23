package co.edu.uco.jjskafee.ingresos.servicios.input.dto.response;

import co.edu.uco.jjskafee.ingresos.dominio.IngresoLibre;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Respuesta de POST /api/ingresos/libres. */
public record IngresoLibreResponse(UUID idIngreso, UUID idCategoria, String categoria, BigDecimal monto,
                                   String descripcion, String comprobante, UUID registradoPor, Instant fechaHora) {

    public static IngresoLibreResponse desde(IngresoLibre i) {
        return new IngresoLibreResponse(i.getId(), i.getCategoria().getId(), i.getCategoria().getNombre(),
                i.getMonto(), i.getDescripcion(), i.getComprobante(), i.getRegistradoPor(), i.getFechaHora());
    }
}
