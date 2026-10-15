package co.edu.uco.jjskafee.compartido.infraestructura.seguridad;

import co.edu.uco.jjskafee.compartido.aplicacion.AutenticarUsuario;
import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.Objects;

/**
 * Autenticación de todas las rutas /api/**: lee "Authorization: Bearer <token>",
 * arma el UsuarioAutenticado y lo deja en la petición.
 *
 * También agrega al MDC de los logs la cafetería, el usuario y el rol. El traceId y el spanId
 * los pone OpenTelemetry (spring-boot-starter-opentelemetry), así cada línea de log queda trazable.
 *
 * Los errores se delegan al HandlerExceptionResolver para que respondan con el mismo formato
 * (y los mismos mensajes de i18n) que el ManejadorErrores.
 */
public class FiltroAutenticacion extends OncePerRequestFilter {

    public static final String ATRIBUTO_USUARIO = FiltroAutenticacion.class.getName() + ".usuario";

    private static final String PREFIJO_API = "/api/";
    private static final String PREFIJO_BEARER = "Bearer ";
    private static final String MDC_CAFETERIA = "idCafeteria";
    private static final String MDC_USUARIO = "idUsuario";
    private static final String MDC_ROL = "rol";

    private final AutenticarUsuario autenticarUsuario;
    private final HandlerExceptionResolver resolutorErrores;

    public FiltroAutenticacion(AutenticarUsuario autenticarUsuario, HandlerExceptionResolver resolutorErrores) {
        this.autenticarUsuario = Objects.requireNonNull(autenticarUsuario);
        this.resolutorErrores = Objects.requireNonNull(resolutorErrores);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest peticion) {
        return "OPTIONS".equalsIgnoreCase(peticion.getMethod())
                || !peticion.getRequestURI().startsWith(peticion.getContextPath() + PREFIJO_API);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        UsuarioAutenticado usuario;
        try {
            usuario = autenticarUsuario.autenticar(extraerToken(peticion));
        } catch (RuntimeException error) {
            resolutorErrores.resolveException(peticion, respuesta, null, error);
            return;
        }

        peticion.setAttribute(ATRIBUTO_USUARIO, usuario);
        MDC.put(MDC_CAFETERIA, usuario.idCafeteria().toString());
        MDC.put(MDC_USUARIO, usuario.id().toString());
        MDC.put(MDC_ROL, usuario.rol().name());
        try {
            cadena.doFilter(peticion, respuesta);
        } finally {
            MDC.remove(MDC_CAFETERIA);
            MDC.remove(MDC_USUARIO);
            MDC.remove(MDC_ROL);
        }
    }

    private static String extraerToken(HttpServletRequest peticion) {
        String encabezado = peticion.getHeader(HttpHeaders.AUTHORIZATION);
        if (encabezado == null || !encabezado.startsWith(PREFIJO_BEARER)) {
            return null;
        }
        return encabezado.substring(PREFIJO_BEARER.length()).strip();
    }
}
