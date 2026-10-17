package co.edu.uco.jjskafee.dominio;

import co.edu.uco.jjskafee.dominio.enums.TipoMovimiento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Una fila de la consulta de ingresos y egresos (viene de la vista v_movimiento). */
public record Movimiento(UUID id, TipoMovimiento tipo, Instant fechaHora, BigDecimal monto,
                         String descripcion, String categoria, String registradoPor) {
}
