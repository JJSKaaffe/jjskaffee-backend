package co.edu.uco.jjskafee.egresos.aplicacion.puertos.salida;

import co.edu.uco.jjskafee.egresos.dominio.Egreso;

/** Tabla egreso (dueño: módulo egresos). */
public interface RepositorioEgresos {

    void guardar(Egreso egreso);
}
