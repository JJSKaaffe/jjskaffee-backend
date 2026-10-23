package co.edu.uco.jjskafee.movimientos.servicios.input.dto.response;

import co.edu.uco.jjskafee.movimientos.dominio.Movimiento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MovimientoResponse(UUID idMovimiento, String tipo, Instant fechaHora, BigDecimal monto,
                                 String descripcion, String categoria, String registradoPor) {

    public static MovimientoResponse desde(Movimiento m) {
        return new MovimientoResponse(m.id(), m.tipo().name(), m.fechaHora(), m.monto(),
                m.descripcion(), m.categoria(), m.registradoPor());
    }
}
