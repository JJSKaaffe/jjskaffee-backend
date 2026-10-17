package co.edu.uco.jjskafee.servicios.output.ports;

import co.edu.uco.jjskafee.dominio.Egreso;

/** Puerto de salida: guardar egresos. */
public interface EgresoRepository {

    void guardar(Egreso egreso);
}
