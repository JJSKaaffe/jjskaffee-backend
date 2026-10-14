package co.edu.uco.jjskafee.servicios.input.dto.request;

import co.edu.uco.jjskafee.dominio.FiltroMovimientos;
import co.edu.uco.jjskafee.dominio.enums.TipoMovimiento;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Filtros de GET /api/movimientos?desde=&hasta=&tipo=&idCategoria=&pagina=&tamano= (todos opcionales).
 * Las fechas se interpretan en la hora de Colombia; 'hasta' incluye todo ese día.
 */
public record FiltroMovimientosRequest(
        LocalDate desde,
        LocalDate hasta,
        TipoMovimiento tipo,
        UUID idCategoria,
        @Min(value = 0, message = "La página debe ser 0 o mayor") Integer pagina,
        @Min(value = 1, message = "El tamaño mínimo es 1")
        @Max(value = FiltroMovimientos.TAMANO_MAXIMO, message = "El tamaño máximo es 50") Integer tamano
) {
    public int paginaOCero() {
        return pagina == null ? 0 : pagina;
    }

    public int tamanoOPorDefecto() {
        return tamano == null ? FiltroMovimientos.TAMANO_POR_DEFECTO : tamano;
    }
}
