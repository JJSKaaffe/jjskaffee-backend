package co.edu.uco.jjskafee.servicios.input.dto.response;

import co.edu.uco.jjskafee.dominio.Egreso;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Respuesta de POST /api/egresos. */
public record EgresoResponse(UUID idEgreso, UUID idCategoria, String categoria, BigDecimal monto,
                             String descripcion, String comprobante, UUID registradoPor, Instant fechaHora) {

    public static EgresoResponse desde(Egreso e) {
        return new EgresoResponse(e.getId(), e.getCategoria().getId(), e.getCategoria().getNombre(), e.getMonto(),
                e.getDescripcion(), e.getComprobante(), e.getRegistradoPor(), e.getFechaHora());
    }
}
