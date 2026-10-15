package co.edu.uco.jjskafee.egresos.aplicacion.puertos.entrada;

import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.egresos.aplicacion.comandos.RegistrarEgresoComando;
import co.edu.uco.jjskafee.egresos.dominio.Egreso;

/** Puerto de entrada: registrar un egreso (RF14). */
public interface RegistrarEgresoCasoUso {

    Egreso registrar(RegistrarEgresoComando comando, UsuarioAutenticado usuario);
}
