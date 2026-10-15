package co.edu.uco.jjskafee.movimientos.dominio;

import co.edu.uco.jjskafee.compartido.dominio.excepciones.ReglaNegocioException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Filtros de la consulta (todos opcionales). Las fechas son días en UTC-0:
 * "desde" incluye desde las 00:00 UTC y "hasta" incluye el día completo.
 * La paginación evita traer todo el historial de una vez (ADR-0004).
 */
public record FiltroMovimientos(LocalDate desde, LocalDate hasta, TipoMovimiento tipo, UUID idCategoria,
                                int pagina, int tamano) {

    public static final int TAMANO_POR_DEFECTO = 50;
    public static final int TAMANO_MAXIMO = 200;

    public static FiltroMovimientos crear(LocalDate desde, LocalDate hasta, TipoMovimiento tipo, UUID idCategoria,
                                          Integer pagina, Integer tamano) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ReglaNegocioException(MensajesMovimientos.FECHAS_INVERTIDAS);
        }
        int numeroPagina = pagina == null ? 0 : pagina;
        int tamanoPagina = tamano == null ? TAMANO_POR_DEFECTO : tamano;
        if (numeroPagina < 0) {
            throw new ReglaNegocioException(MensajesMovimientos.PAGINA_INVALIDA);
        }
        if (tamanoPagina < 1 || tamanoPagina > TAMANO_MAXIMO) {
            throw new ReglaNegocioException(MensajesMovimientos.TAMANO_INVALIDO, TAMANO_MAXIMO);
        }
        return new FiltroMovimientos(desde, hasta, tipo, idCategoria, numeroPagina, tamanoPagina);
    }

    /** Inicio inclusivo en UTC, o null si no hay "desde". */
    public Instant inicioUtc() {
        return desde == null ? null : desde.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    /** Fin EXCLUSIVO en UTC (día siguiente a "hasta" a las 00:00), o null si no hay "hasta". */
    public Instant finUtcExclusivo() {
        return hasta == null ? null : hasta.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    public long desplazamiento() {
        return (long) pagina * tamano;
    }
}
