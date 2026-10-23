package co.edu.uco.jjskafee.movimientos.infraestructura.input.controladores;

import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.compartido.infraestructura.input.AtributosDePeticion;
import co.edu.uco.jjskafee.movimientos.servicios.input.dto.request.FiltroMovimientosRequest;
import co.edu.uco.jjskafee.movimientos.servicios.input.dto.response.MovimientosResponse;
import co.edu.uco.jjskafee.movimientos.servicios.input.ports.ConsultarMovimientosUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador de entrada HTTP para consultar movimientos. Solo conoce los puertos de entrada.
 * El usuario sale del token (FirebaseAuthFilter), nunca del cuerpo de la petición (ADR-0007).
 * El API Gateway expone estas rutas con el prefijo /api.
 */
@RestController
public class MovimientosController {

    private final ConsultarMovimientosUseCase consultarMovimientos;

    public MovimientosController(ConsultarMovimientosUseCase consultarMovimientos) {
        this.consultarMovimientos = consultarMovimientos;
    }

    /** Historial paginado con totales (RF9, RF17, RF38). */
    @GetMapping("/movimientos")
    public MovimientosResponse consultarMovimientos(@Valid @ModelAttribute FiltroMovimientosRequest filtro,
                                                    @RequestAttribute(AtributosDePeticion.USUARIO) UsuarioAutenticado usuario) {
        return consultarMovimientos.consultar(filtro, usuario);
    }
}
