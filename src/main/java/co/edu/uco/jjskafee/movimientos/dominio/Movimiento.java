package co.edu.uco.jjskafee.movimientos.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Una fila de la consulta de ingresos y egresos (vista v_movimiento).
 * @param categoria      nombre de la categoría (null en los ingresos)
 * @param registradoPor  nombre de quien lo registró
 */
public record Movimiento(UUID id, TipoMovimiento tipo, Instant fechaHora, BigDecimal monto,
                         String descripcion, String categoria, String registradoPor) {

    public Movimiento conDescripcion(String nuevaDescripcion) {
        return new Movimiento(id, tipo, fechaHora, monto, nuevaDescripcion, categoria, registradoPor);
    }
}
