package co.edu.uco.jjskafee.dominio;

import co.edu.uco.jjskafee.dominio.enums.TipoMovimiento;
import co.edu.uco.jjskafee.dominio.excepciones.ReglaNegocioException;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Criterios para consultar movimientos (RF38): todos opcionales menos la página.
 * Los historiales siempre se paginan, con máximo 50 registros por página (ADR-0004).
 *
 * @param soloDelUsuario si no es null, solo lo que registró ese usuario (RF9: el cajero ve lo suyo)
 */
public record FiltroMovimientos(LocalDate desde, LocalDate hasta, TipoMovimiento tipo, UUID idCategoria,
                                UUID soloDelUsuario, int pagina, int tamano) {

    public static final int TAMANO_POR_DEFECTO = 20;
    public static final int TAMANO_MAXIMO = 50;

    public FiltroMovimientos {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ReglaNegocioException("La fecha 'desde' no puede ser posterior a 'hasta'");
        }
        if (pagina < 0) {
            throw new ReglaNegocioException("La página debe ser 0 o mayor");
        }
        if (tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw new ReglaNegocioException("El tamaño de página debe estar entre 1 y " + TAMANO_MAXIMO);
        }
    }

    /** Cuántos registros saltar para llegar a la página pedida. */
    public long desplazamiento() {
        return (long) pagina * tamano;
    }
}
