package co.edu.uco.jjskafee.infraestructura.input.controladores;

import co.edu.uco.jjskafee.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.infraestructura.input.seguridad.FirebaseAuthFilter;
import co.edu.uco.jjskafee.servicios.input.dto.request.EgresoRequest;
import co.edu.uco.jjskafee.servicios.input.dto.request.FiltroMovimientosRequest;
import co.edu.uco.jjskafee.servicios.input.dto.request.IngresoRequest;
import co.edu.uco.jjskafee.servicios.input.dto.response.EgresoResponse;
import co.edu.uco.jjskafee.servicios.input.dto.response.IngresoResponse;
import co.edu.uco.jjskafee.servicios.input.dto.response.MovimientosResponse;
import co.edu.uco.jjskafee.servicios.input.ports.ConsultarMovimientosUseCase;
import co.edu.uco.jjskafee.servicios.input.ports.RegistrarEgresoUseCase;
import co.edu.uco.jjskafee.servicios.input.ports.RegistrarIngresoUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Adaptador de entrada HTTP para ingresos y egresos. Solo conoce los puertos de entrada.
 * El usuario sale del token (FirebaseAuthFilter), nunca del cuerpo de la petición (ADR-0007).
 * El API Gateway expone estas rutas con el prefijo /api.
 */
@RestController
public class MovimientosController {

    private final RegistrarIngresoUseCase registrarIngreso;
    private final RegistrarEgresoUseCase registrarEgreso;
    private final ConsultarMovimientosUseCase consultarMovimientos;

    public MovimientosController(RegistrarIngresoUseCase registrarIngreso, RegistrarEgresoUseCase registrarEgreso,
                                 ConsultarMovimientosUseCase consultarMovimientos) {
        this.registrarIngreso = registrarIngreso;
        this.registrarEgreso = registrarEgreso;
        this.consultarMovimientos = consultarMovimientos;
    }

    /** Registra una venta con sus productos y adiciones (RF24–RF27). */
    @PostMapping("/ingresos")
    @ResponseStatus(HttpStatus.CREATED)
    public IngresoResponse registrarIngreso(@Valid @RequestBody IngresoRequest solicitud,
                                            @RequestAttribute(FirebaseAuthFilter.USUARIO) UsuarioAutenticado usuario) {
        return registrarIngreso.registrar(solicitud, usuario);
    }

    /** Deshace una venta: solo quien la registró y dentro de los 60 segundos (ADR-0008). */
    @PostMapping("/ingresos/{idVenta}/deshacer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deshacerIngreso(@PathVariable UUID idVenta,
                                @RequestAttribute(FirebaseAuthFilter.USUARIO) UsuarioAutenticado usuario) {
        registrarIngreso.deshacer(idVenta, usuario);
    }

    /** Registra un egreso; solo el administrador (RF14). */
    @PostMapping("/egresos")
    @ResponseStatus(HttpStatus.CREATED)
    public EgresoResponse registrarEgreso(@Valid @RequestBody EgresoRequest solicitud,
                                          @RequestAttribute(FirebaseAuthFilter.USUARIO) UsuarioAutenticado usuario) {
        return registrarEgreso.registrar(solicitud, usuario);
    }

    /** Historial paginado con totales (RF9, RF17, RF38). */
    @GetMapping("/movimientos")
    public MovimientosResponse consultarMovimientos(@Valid @ModelAttribute FiltroMovimientosRequest filtro,
                                                    @RequestAttribute(FirebaseAuthFilter.USUARIO) UsuarioAutenticado usuario) {
        return consultarMovimientos.consultar(filtro, usuario);
    }
}
