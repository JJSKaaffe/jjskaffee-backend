package co.edu.uco.jjskafee.acceso.servicios.input.ports;

import co.edu.uco.jjskafee.acceso.dominio.Identidad;
import co.edu.uco.jjskafee.acceso.servicios.input.dto.response.CafeteriaDelUsuarioResponse;
import co.edu.uco.jjskafee.acceso.servicios.input.dto.response.UsuarioActualResponse;
import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;

import java.util.List;

/** Puerto de entrada: lo que el frontend necesita para elegir cafetería y armar el menú. */
public interface ConsultarAccesoUseCase {

    /** Cafeterías donde la persona tiene una membresía activa, con su rol en cada una. */
    List<CafeteriaDelUsuarioResponse> misCafeterias(Identidad identidad);

    /** El usuario en la cafetería elegida, con los permisos de su rol. */
    UsuarioActualResponse yo(UsuarioAutenticado usuario);
}
