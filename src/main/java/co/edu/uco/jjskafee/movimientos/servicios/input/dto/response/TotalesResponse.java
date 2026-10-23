package co.edu.uco.jjskafee.movimientos.servicios.input.dto.response;

import java.math.BigDecimal;

public record TotalesResponse(BigDecimal ingresos, BigDecimal egresos, BigDecimal balance) {
}
