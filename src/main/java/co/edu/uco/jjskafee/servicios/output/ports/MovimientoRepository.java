package co.edu.uco.jjskafee.servicios.output.ports;

import co.edu.uco.jjskafee.dominio.Movimiento;
import co.edu.uco.jjskafee.dominio.enums.TipoMovimiento;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Puerto de salida: consultar ingresos y egresos (vista v_movimiento). */
public interface MovimientoRepository {

    /**
     * @param tipo           INGRESO, EGRESO o null para ambos
     * @param soloDelUsuario si no es null, solo trae lo que registró ese usuario (RF9)
     */
    List<Movimiento> buscar(LocalDate desde, LocalDate hasta, TipoMovimiento tipo, UUID soloDelUsuario);
}
