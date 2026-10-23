package co.edu.uco.jjskafee.egresos.infraestructura.input.controladores;

import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.compartido.infraestructura.input.AtributosDePeticion;
import co.edu.uco.jjskafee.egresos.servicios.input.dto.request.EgresoRequest;
import co.edu.uco.jjskafee.egresos.servicios.input.dto.response.EgresoResponse;
import co.edu.uco.jjskafee.egresos.servicios.input.ports.RegistrarEgresoUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador de entrada HTTP para egresos. Solo conoce los puertos de entrada.
 * El usuario sale del token (FirebaseAuthFilter), nunca del cuerpo de la petición (ADR-0007).
 * El API Gateway expone estas rutas con el prefijo /api.
 */
@RestController
public class EgresosController {

    private final RegistrarEgresoUseCase registrarEgreso;

    public EgresosController(RegistrarEgresoUseCase registrarEgreso) {
        this.registrarEgreso = registrarEgreso;
    }

    /** Registra un egreso; solo el administrador (RF14). */
    @PostMapping("/egresos")
    @ResponseStatus(HttpStatus.CREATED)
    public EgresoResponse registrarEgreso(@Valid @RequestBody EgresoRequest solicitud,
                                          @RequestAttribute(AtributosDePeticion.USUARIO) UsuarioAutenticado usuario) {
        return registrarEgreso.registrar(solicitud, usuario);
    }
}
