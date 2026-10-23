package co.edu.uco.jjskafee.movimientos.servicios.output.ports;

import co.edu.uco.jjskafee.movimientos.dominio.FiltroMovimientos;
import co.edu.uco.jjskafee.movimientos.dominio.ResultadoMovimientos;

/** Puerto de salida: consultar ingresos y egresos (vista v_movimiento). */
public interface MovimientoRepository {

    /**
     * Devuelve la página pedida (más recientes primero) y los totales de todo el filtro,
     * calculados con SUM en la base de datos (ADR-0004).
     */
    ResultadoMovimientos buscar(FiltroMovimientos filtro);
}
