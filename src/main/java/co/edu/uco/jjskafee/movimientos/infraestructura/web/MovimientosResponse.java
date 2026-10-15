package co.edu.uco.jjskafee.movimientos.infraestructura.web;

import co.edu.uco.jjskafee.movimientos.dominio.Movimiento;
import co.edu.uco.jjskafee.movimientos.dominio.PaginaMovimientos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Respuesta de GET /api/movimientos: la página, los totales del filtro completo y la paginación. */
public record MovimientosResponse(List<MovimientoResponse> movimientos, TotalesResponse totales,
                                  PaginaResponse pagina) {

    public static MovimientosResponse desde(PaginaMovimientos p) {
        return new MovimientosResponse(
                p.movimientos().stream().map(MovimientoResponse::desde).toList(),
                new TotalesResponse(p.totales().ingresos(), p.totales().egresos(), p.totales().balance()),
                new PaginaResponse(p.pagina(), p.tamano(), p.totalElementos(), p.totalPaginas()));
    }

    public record MovimientoResponse(UUID idMovimiento, String tipo, Instant fechaHora, BigDecimal monto,
                                     String descripcion, String categoria, String registradoPor) {
        static MovimientoResponse desde(Movimiento m) {
            return new MovimientoResponse(m.id(), m.tipo().name(), m.fechaHora(), m.monto(), m.descripcion(),
                    m.categoria(), m.registradoPor());
        }
    }

    public record TotalesResponse(BigDecimal ingresos, BigDecimal egresos, BigDecimal balance) {
    }

    public record PaginaResponse(int numero, int tamano, long totalElementos, long totalPaginas) {
    }
}
