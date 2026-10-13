package co.edu.uco.jjskafee.servicios.input.ports;

import co.edu.uco.jjskafee.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.servicios.input.dto.request.IngresoRequest;
import co.edu.uco.jjskafee.servicios.input.dto.response.IngresoResponse;

import java.util.UUID;

/** Puerto de entrada: registrar una venta (RF24–RF27) y deshacerla (ADR-0008). */
public interface RegistrarIngresoUseCase {

    IngresoResponse registrar(IngresoRequest solicitud, UsuarioAutenticado usuario);

    void deshacer(UUID idVenta, UsuarioAutenticado usuario);
}
