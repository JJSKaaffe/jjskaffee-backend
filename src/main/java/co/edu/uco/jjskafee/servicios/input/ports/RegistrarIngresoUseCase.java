package co.edu.uco.jjskafee.servicios.input.ports;

import co.edu.uco.jjskafee.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.servicios.input.dto.request.IngresoRequest;
import co.edu.uco.jjskafee.servicios.input.dto.response.IngresoResponse;

import java.util.UUID;

/** Puerto de entrada: registrar una venta (RF24–RF27) y deshacerla (ADR-0008). */
public interface RegistrarIngresoUseCase {

    /**
     * @param claveIdempotencia encabezado Idempotency-Key (puede ser null). Con la misma clave y la
     *                          misma solicitud devuelve la venta ya registrada en vez de crear otra.
     */
    IngresoResponse registrar(IngresoRequest solicitud, UUID claveIdempotencia, UsuarioAutenticado usuario);

    /** Registra sin Idempotency-Key: cada llamada es una venta nueva. */
    default IngresoResponse registrar(IngresoRequest solicitud, UsuarioAutenticado usuario) {
        return registrar(solicitud, null, usuario);
    }

    void deshacer(UUID idVenta, UsuarioAutenticado usuario);
}
