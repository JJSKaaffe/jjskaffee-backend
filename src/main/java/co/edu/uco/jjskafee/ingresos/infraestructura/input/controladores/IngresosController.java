package co.edu.uco.jjskafee.ingresos.infraestructura.input.controladores;

import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.compartido.infraestructura.input.AtributosDePeticion;
import co.edu.uco.jjskafee.compartido.infraestructura.input.Encabezados;
import co.edu.uco.jjskafee.ingresos.servicios.input.dto.request.IngresoLibreRequest;
import co.edu.uco.jjskafee.ingresos.servicios.input.dto.request.IngresoRequest;
import co.edu.uco.jjskafee.ingresos.servicios.input.dto.response.CatalogoDeVentaResponse;
import co.edu.uco.jjskafee.ingresos.servicios.input.dto.response.IngresoLibreResponse;
import co.edu.uco.jjskafee.ingresos.servicios.input.dto.response.IngresoResponse;
import co.edu.uco.jjskafee.ingresos.servicios.input.ports.ConsultarCatalogoDeVentaUseCase;
import co.edu.uco.jjskafee.ingresos.servicios.input.ports.RegistrarIngresoLibreUseCase;
import co.edu.uco.jjskafee.ingresos.servicios.input.ports.RegistrarIngresoUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Adaptador de entrada HTTP para ingresos: ventas e ingresos libres. Solo conoce los puertos de entrada.
 * El usuario sale del token (FirebaseAuthFilter), nunca del cuerpo de la petición (ADR-0007).
 * El API Gateway expone estas rutas con el prefijo /api.
 */
@RestController
public class IngresosController {

    private final RegistrarIngresoUseCase registrarIngreso;
    private final RegistrarIngresoLibreUseCase registrarIngresoLibre;
    private final ConsultarCatalogoDeVentaUseCase consultarCatalogo;

    public IngresosController(RegistrarIngresoUseCase registrarIngreso,
                              RegistrarIngresoLibreUseCase registrarIngresoLibre,
                              ConsultarCatalogoDeVentaUseCase consultarCatalogo) {
        this.registrarIngreso = registrarIngreso;
        this.registrarIngresoLibre = registrarIngresoLibre;
        this.consultarCatalogo = consultarCatalogo;
    }

    /**
     * Lo que muestra la pantalla de venta: productos base disponibles con sus adiciones y precios, y
     * (solo para el administrador) las categorías de ingreso activas para los ingresos libres.
     */
    @GetMapping("/ingresos/catalogo")
    public CatalogoDeVentaResponse catalogo(@RequestAttribute(AtributosDePeticion.USUARIO) UsuarioAutenticado usuario) {
        return consultarCatalogo.consultar(usuario);
    }

    /**
     * Registra una venta con sus productos y adiciones (RF24–RF27).
     * Idempotency-Key (UUID, opcional): un reintento con la misma clave devuelve la venta ya registrada.
     */
    @PostMapping("/ingresos")
    @ResponseStatus(HttpStatus.CREATED)
    public IngresoResponse registrarIngreso(@Valid @RequestBody IngresoRequest solicitud,
                                            @RequestHeader(value = Encabezados.IDEMPOTENCIA, required = false)
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

    /**
     * Registra un ingreso libre (monto + categoría de ingreso); solo el administrador.
     * Idempotency-Key (UUID, opcional): un reintento con la misma clave devuelve el ingreso ya registrado.
     */
    @PostMapping("/ingresos/libres")
    @ResponseStatus(HttpStatus.CREATED)
    public IngresoLibreResponse registrarIngresoLibre(@Valid @RequestBody IngresoLibreRequest solicitud,
                                                      @RequestHeader(value = Encabezados.IDEMPOTENCIA, required = false)
                                                      UUID claveIdempotencia,
                                                      @RequestAttribute(AtributosDePeticion.USUARIO) UsuarioAutenticado usuario) {
        return registrarIngresoLibre.registrar(solicitud, claveIdempotencia, usuario);
    }
}
