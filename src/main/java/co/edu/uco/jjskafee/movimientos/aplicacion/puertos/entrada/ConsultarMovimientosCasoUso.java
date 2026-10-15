package co.edu.uco.jjskafee.movimientos.aplicacion.puertos.entrada;

import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.movimientos.dominio.FiltroMovimientos;
import co.edu.uco.jjskafee.movimientos.dominio.PaginaMovimientos;

/** Puerto de entrada: consultar ingresos y egresos con totales (RF9, RF17, RF38). */
public interface ConsultarMovimientosCasoUso {

    PaginaMovimientos consultar(FiltroMovimientos filtro, UsuarioAutenticado usuario);
}
