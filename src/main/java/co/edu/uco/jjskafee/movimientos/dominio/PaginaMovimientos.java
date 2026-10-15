package co.edu.uco.jjskafee.movimientos.dominio;

import java.util.List;

/**
 * Una página de movimientos con los totales de TODO el filtro (no solo de la página).
 * @param totalElementos cantidad de movimientos que cumplen el filtro
 */
public record PaginaMovimientos(List<Movimiento> movimientos, Totales totales, int pagina, int tamano,
                                long totalElementos) {

    public PaginaMovimientos {
        movimientos = List.copyOf(movimientos);
    }

    public long totalPaginas() {
        return totalElementos == 0 ? 0 : (totalElementos + tamano - 1) / tamano;
    }
}
