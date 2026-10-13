package co.edu.uco.jjskafee.servicios.input.dto.response;

import co.edu.uco.jjskafee.dominio.ResultadoMovimientos;

import java.util.List;

/** Respuesta de GET /api/movimientos: la lista y los totales. */
public record MovimientosResponse(List<MovimientoResponse> movimientos, TotalesResponse totales) {

    public static MovimientosResponse desde(ResultadoMovimientos r) {
        return new MovimientosResponse(
                r.movimientos().stream().map(MovimientoResponse::desde).toList(),
                new TotalesResponse(r.ingresos(), r.egresos(), r.balance()));
    }
}
