package co.edu.uco.jjskafee.dominio;

import co.edu.uco.jjskafee.dominio.enums.TipoMovimiento;

import java.math.BigDecimal;
import java.util.List;

/** Movimientos consultados con sus totales. */
public record ResultadoMovimientos(List<Movimiento> movimientos, BigDecimal ingresos, BigDecimal egresos) {

    /** Calcula los totales a partir de la lista de movimientos. */
    public static ResultadoMovimientos de(List<Movimiento> movimientos) {
        BigDecimal ingresos = sumar(movimientos, TipoMovimiento.INGRESO);
        BigDecimal egresos = sumar(movimientos, TipoMovimiento.EGRESO);
        return new ResultadoMovimientos(List.copyOf(movimientos), ingresos, egresos);
    }

    public BigDecimal balance() {
        return ingresos.subtract(egresos);
    }

    private static BigDecimal sumar(List<Movimiento> movimientos, TipoMovimiento tipo) {
        return movimientos.stream()
                .filter(m -> m.tipo() == tipo)
                .map(Movimiento::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
