package co.edu.uco.jjskafee.movimientos.servicios.input.dto.response;

import co.edu.uco.jjskafee.movimientos.dominio.FiltroMovimientos;
import co.edu.uco.jjskafee.movimientos.dominio.ResultadoMovimientos;

import java.util.List;

/**
 * Respuesta de GET /api/movimientos: una página de la lista y los totales de todo el filtro.
 * 'totalMovimientos' es la cantidad de movimientos que cumplen el filtro (para armar la paginación).
 */
public record MovimientosResponse(List<MovimientoResponse> movimientos, TotalesResponse totales,
                                  int pagina, int tamano, long totalMovimientos) {

    public static MovimientosResponse desde(ResultadoMovimientos r, FiltroMovimientos filtro) {
        return new MovimientosResponse(
                r.movimientos().stream().map(MovimientoResponse::desde).toList(),
                new TotalesResponse(r.ingresos(), r.egresos(), r.balance()),
                filtro.pagina(), filtro.tamano(), r.totalMovimientos());
    }
}
