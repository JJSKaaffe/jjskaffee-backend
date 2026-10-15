package co.edu.uco.jjskafee.movimientos.aplicacion.puertos.salida;

import co.edu.uco.jjskafee.movimientos.dominio.FiltroMovimientos;
import co.edu.uco.jjskafee.movimientos.dominio.Movimiento;
import co.edu.uco.jjskafee.movimientos.dominio.ResumenMovimientos;

import java.util.List;
import java.util.UUID;

/**
 * Lectura de la vista v_movimiento (modelo de consulta: no escribe nada).
 * Si soloDelUsuario no es null, limita a lo que registró ese usuario (RF9).
 */
public interface RepositorioMovimientos {

    /** Una página, de lo más reciente a lo más antiguo. */
    List<Movimiento> buscar(UUID idCafeteria, FiltroMovimientos filtro, UUID soloDelUsuario);

    /** Totales con SUM en la BD (ADR-0004) y cantidad de filas, para todo el filtro. */
    ResumenMovimientos resumir(UUID idCafeteria, FiltroMovimientos filtro, UUID soloDelUsuario);
}
