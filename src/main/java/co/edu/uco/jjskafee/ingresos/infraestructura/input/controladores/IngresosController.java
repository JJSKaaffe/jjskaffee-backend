package co.edu.uco.jjskafee.ingresos.infraestructura.input.controladores;

import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.compartido.infraestructura.input.AtributosDePeticion;
import co.edu.uco.jjskafee.ingresos.servicios.input.dto.request.IngresoRequest;
import co.edu.uco.jjskafee.ingresos.servicios.input.dto.response.IngresoResponse;
import co.edu.uco.jjskafee.ingresos.servicios.input.ports.RegistrarIngresoUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Adaptador de entrada HTTP para ingresos. Solo conoce los puertos de entrada.
 * El usuario sale del token (FirebaseAuthFilter), nunca del cuerpo de la petición (ADR-0007).
 * El API Gateway expone estas rutas con el prefijo /api.
 */
@RestController
public class IngresosController {

    public static final String ENCABEZADO_IDEMPOTENCIA = "Idempotency-Key";

    private final RegistrarIngresoUseCase registrarIngreso;

    public IngresosController(RegistrarIngresoUseCase registrarIngreso) {
        this.registrarIngreso = registrarIngreso;
    }

    /**
     * Registra una venta con sus productos y adiciones (RF24–RF27).
     * Idempotency-Key (UUID, opcional): un reintento con la misma clave devuelve la venta ya registrada.
     */
    @PostMapping("/ingresos")
    @ResponseStatus(HttpStatus.CREATED)
    public IngresoResponse registrarIngreso(@Valid @RequestBody IngresoRequest solicitud,
                                            @RequestHeader(value = ENCABEZADO_IDEMPOTENCIA, required = false)
                                            UUID claveIdempotencia,
                                            @RequestAttribute(AtributosDePeticion.USUARIO) UsuarioAutenticado usuario) {
        return registrarIngreso.registrar(solicitud, claveIdempotencia, usuario);
    }

    /** Deshace una venta: solo quien la registró y dentro de los 60 segundos (ADR-0008). */
    @PostMapping("/ingresos/{idVenta}/deshacer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deshacerIngreso(@PathVariable UUID idVenta,
                                @RequestAttribute(AtributosDePeticion.USUARIO) UsuarioAutenticado usuario) {
        registrarIngreso.deshacer(idVenta, usuario);
    }
}
