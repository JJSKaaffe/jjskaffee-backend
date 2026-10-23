package co.edu.uco.jjskafee.ingresos.servicios.input.ports;

import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.ingresos.servicios.input.dto.request.IngresoLibreRequest;
import co.edu.uco.jjskafee.ingresos.servicios.input.dto.response.IngresoLibreResponse;

import java.util.UUID;

/** Puerto de entrada: registrar un ingreso libre (monto + categoría de ingreso); solo el administrador. */
public interface RegistrarIngresoLibreUseCase {

    /**
     * @param claveIdempotencia encabezado Idempotency-Key (puede ser null). Con la misma clave y la
     *                          misma solicitud devuelve el ingreso ya registrado en vez de crear otro.
     */
    IngresoLibreResponse registrar(IngresoLibreRequest solicitud, UUID claveIdempotencia, UsuarioAutenticado usuario);

    /** Registra sin Idempotency-Key: cada llamada es un ingreso nuevo. */
    default IngresoLibreResponse registrar(IngresoLibreRequest solicitud, UsuarioAutenticado usuario) {
        return registrar(solicitud, null, usuario);
    }
}
