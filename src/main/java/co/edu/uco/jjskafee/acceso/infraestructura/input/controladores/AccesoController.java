package co.edu.uco.jjskafee.acceso.infraestructura.input.controladores;

import co.edu.uco.jjskafee.acceso.dominio.Identidad;
import co.edu.uco.jjskafee.acceso.servicios.input.dto.response.CafeteriaDelUsuarioResponse;
import co.edu.uco.jjskafee.acceso.servicios.input.dto.response.UsuarioActualResponse;
import co.edu.uco.jjskafee.acceso.servicios.input.ports.ConsultarAccesoUseCase;
import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.compartido.infraestructura.input.AtributosDePeticion;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Adaptador de entrada HTTP para elegir cafetería y conocer el rol (ADR-0016). Solo conoce los puertos de entrada.
 * El API Gateway expone estas rutas con el prefijo /api.
 */
@RestController
public class AccesoController {

    private final ConsultarAccesoUseCase consultarAcceso;

    public AccesoController(ConsultarAccesoUseCase consultarAcceso) {
        this.consultarAcceso = consultarAcceso;
    }

    /** Cafeterías del usuario con su rol en cada una. Solo con el token, sin X-Cafeteria-Id. */
    @GetMapping("/acceso/cafeterias")
    public List<CafeteriaDelUsuarioResponse> misCafeterias(
            @RequestAttribute(AtributosDePeticion.IDENTIDAD) Identidad identidad) {
        return consultarAcceso.misCafeterias(identidad);
    }

    /** Rol y permisos en la cafetería elegida (X-Cafeteria-Id); el frontend arma el menú con esto. */
    @GetMapping("/acceso/yo")
    public UsuarioActualResponse yo(@RequestAttribute(AtributosDePeticion.USUARIO) UsuarioAutenticado usuario) {
        return consultarAcceso.yo(usuario);
    }
}
