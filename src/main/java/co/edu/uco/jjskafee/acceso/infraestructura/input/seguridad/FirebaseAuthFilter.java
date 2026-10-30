package co.edu.uco.jjskafee.acceso.infraestructura.input.seguridad;

import co.edu.uco.jjskafee.acceso.dominio.Identidad;
import co.edu.uco.jjskafee.acceso.servicios.input.ports.AutenticarUsuarioUseCase;
import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.NoAutenticadoException;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.SinPermisoException;
import co.edu.uco.jjskafee.compartido.infraestructura.input.AtributosDePeticion;
import co.edu.uco.jjskafee.compartido.infraestructura.input.Encabezados;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Exige un token de Firebase en "Authorization: Bearer ..." y la cafetería elegida en X-Cafeteria-Id
 * en todas las peticiones (ADR-0016), y deja en la petición el UsuarioAutenticado con el rol de esa
 * cafetería. GET /acceso/cafeterias solo pide el token: deja la Identidad para listar las cafeterías.
 * Sin token válido → 401; falta el encabezado o no es UUID → 400; sin membresía activa → 403.
 * Detrás del API Gateway el token de Firebase llega en X-Forwarded-Authorization (ADR-0017).
 */
@Component
public class FirebaseAuthFilter extends OncePerRequestFilter {

    /** Única ruta que no lleva cafetería: el usuario todavía no ha elegido una. */
    static final String RUTA_MIS_CAFETERIAS = "/acceso/cafeterias";

    /**
     * El API Gateway llama a Cloud Run con el token de su cuenta de servicio en Authorization y copia aquí
     * el token original del usuario (ADR-0017). Si viene, gana sobre Authorization.
     */
    static final String AUTORIZACION_REENVIADA = "X-Forwarded-Authorization";

    private static final String PREFIJO = "Bearer ";

    private final AutenticarUsuarioUseCase autenticar;

    public FirebaseAuthFilter(AutenticarUsuarioUseCase autenticar) {
        this.autenticar = Objects.requireNonNull(autenticar);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // El preflight de CORS no lleva token
        return HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Identidad identidad;
        try {
            identidad = autenticar.identificar(token(request));
        } catch (NoAutenticadoException e) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            responder(response, HttpServletResponse.SC_UNAUTHORIZED, "No autenticado", e.getMessage());
            return;
        }

        if (RUTA_MIS_CAFETERIAS.equals(ruta(request))) {
            request.setAttribute(AtributosDePeticion.IDENTIDAD, identidad);
            chain.doFilter(request, response);
            return;
        }

        String cafeteria = request.getHeader(Encabezados.CAFETERIA);
        if (cafeteria == null || cafeteria.isBlank()) {
            responder(response, HttpServletResponse.SC_BAD_REQUEST, "Datos inválidos",
                    "Falta el encabezado " + Encabezados.CAFETERIA + " con la cafetería elegida");
            return;
        }
        UUID idCafeteria;
        try {
            idCafeteria = UUID.fromString(cafeteria.strip());
        } catch (IllegalArgumentException e) {
            responder(response, HttpServletResponse.SC_BAD_REQUEST, "Datos inválidos",
                    "El encabezado " + Encabezados.CAFETERIA + " debe ser un UUID");
            return;
        }

        UsuarioAutenticado usuario;
        try {
            usuario = autenticar.autorizarEnCafeteria(identidad, idCafeteria);
        } catch (SinPermisoException e) {
            responder(response, HttpServletResponse.SC_FORBIDDEN, "Sin permiso", e.getMessage());
            return;
        }
        request.setAttribute(AtributosDePeticion.USUARIO, usuario);
        chain.doFilter(request, response);
    }

    private static String token(HttpServletRequest request) {
        // En la nube viene del gateway; en local solo hay Authorization
        String encabezado = request.getHeader(AUTORIZACION_REENVIADA);
        if (encabezado == null || encabezado.isBlank()) {
            encabezado = request.getHeader(HttpHeaders.AUTHORIZATION);
        }
        if (encabezado == null || !encabezado.regionMatches(true, 0, PREFIJO, 0, PREFIJO.length())) {
            throw new NoAutenticadoException("Falta el token de inicio de sesión");
        }
        return encabezado.substring(PREFIJO.length()).strip();
    }

    private static String ruta(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    /** Responde en el mismo formato ProblemDetail que ManejadorDeErrores. Los mensajes son fijos (sin datos del usuario). */
    private static void responder(HttpServletResponse response, int estado, String titulo, String mensaje)
            throws IOException {
        response.setStatus(estado);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"title":"%s","status":%d,"detail":"%s"}""".formatted(titulo, estado, mensaje.replace("\"", "'")));
    }
}
