package co.edu.uco.jjskafee.servicios.input.ports;

import co.edu.uco.jjskafee.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.servicios.input.dto.request.EgresoRequest;
import co.edu.uco.jjskafee.servicios.input.dto.response.EgresoResponse;

/** Puerto de entrada: registrar un egreso (RF14, RF18). */
public interface RegistrarEgresoUseCase {

    EgresoResponse registrar(EgresoRequest solicitud, UsuarioAutenticado usuario);
}
