package co.edu.uco.jjskafee.egresos.servicios.output.ports;

import co.edu.uco.jjskafee.egresos.dominio.Egreso;

/** Puerto de salida: guardar egresos. */
public interface EgresoRepository {

    void guardar(Egreso egreso);
}
