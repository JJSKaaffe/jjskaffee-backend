package co.edu.uco.jjskafee.servicios.input.dto.request;

import co.edu.uco.jjskafee.dominio.enums.TipoMovimiento;

import java.time.LocalDate;

/** Filtros de GET /api/movimientos?desde=&hasta=&tipo= (todos opcionales). */
public record FiltroMovimientosRequest(LocalDate desde, LocalDate hasta, TipoMovimiento tipo) {
}
