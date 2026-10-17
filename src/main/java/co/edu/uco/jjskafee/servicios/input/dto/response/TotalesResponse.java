package co.edu.uco.jjskafee.servicios.input.dto.response;

import java.math.BigDecimal;

public record TotalesResponse(BigDecimal ingresos, BigDecimal egresos, BigDecimal balance) {
}
