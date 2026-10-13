package co.edu.uco.jjskafee.servicios.input.ports;

import co.edu.uco.jjskafee.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.servicios.input.dto.request.FiltroMovimientosRequest;
import co.edu.uco.jjskafee.servicios.input.dto.response.MovimientosResponse;

/** Puerto de entrada: consultar ingresos y egresos con totales (RF9: el cajero solo ve lo suyo). */
public interface ConsultarMovimientosUseCase {

    MovimientosResponse consultar(FiltroMovimientosRequest filtro, UsuarioAutenticado usuario);
}
