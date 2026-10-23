package co.edu.uco.jjskafee.egresos.servicios.input.ports;

import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.egresos.servicios.input.dto.request.EgresoRequest;
import co.edu.uco.jjskafee.egresos.servicios.input.dto.response.EgresoResponse;

/** Puerto de entrada: registrar un egreso (RF14, RF18). */
public interface RegistrarEgresoUseCase {

    EgresoResponse registrar(EgresoRequest solicitud, UsuarioAutenticado usuario);
}
