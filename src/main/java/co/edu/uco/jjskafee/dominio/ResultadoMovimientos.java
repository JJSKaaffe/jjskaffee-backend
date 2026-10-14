package co.edu.uco.jjskafee.dominio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Una página de movimientos con los totales de TODO el filtro (no solo de la página).
 * Los totales los calcula la base de datos con SUM; nunca se suman en Java (ADR-0004).
 */
public record ResultadoMovimientos(List<Movimiento> movimientos, BigDecimal ingresos, BigDecimal egresos,
                                   long totalMovimientos) {

    public ResultadoMovimientos {
        movimientos = List.copyOf(movimientos);
        Objects.requireNonNull(ingresos, "El total de ingresos es obligatorio");
        Objects.requireNonNull(egresos, "El total de egresos es obligatorio");
    }

    public BigDecimal balance() {
        return ingresos.subtract(egresos);
    }
}
