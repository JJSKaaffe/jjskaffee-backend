package co.edu.uco.jjskafee.movimientos.dominio;

import java.math.BigDecimal;
import java.util.Objects;

/** Totales calculados en la BD con SUM (ADR-0004), no sumando en Java. */
public record Totales(BigDecimal ingresos, BigDecimal egresos) {

    public Totales {
        Objects.requireNonNull(ingresos);
        Objects.requireNonNull(egresos);
    }

    public BigDecimal balance() {
        return ingresos.subtract(egresos);
    }
}
