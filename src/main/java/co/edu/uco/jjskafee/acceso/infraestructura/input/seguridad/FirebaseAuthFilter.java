package co.edu.uco.jjskafee.acceso.infraestructura.input.seguridad;

import co.edu.uco.jjskafee.acceso.servicios.output.ports.VerificadorDeToken;
import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.NoAutenticadoException;
import co.edu.uco.jjskafee.compartido.infraestructura.input.AtributosDePeticion;
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

/**
 * Exige un token de Firebase en "Authorization: Bearer ..." en todas las peticiones,
 * lo verifica y deja el UsuarioAutenticado en la petición para el controller.
 * Sin token válido responde 401 y la petición no llega al controller.
 */
@Component
public class FirebaseAuthFilter extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final VerificadorDeToken verificador;

    public FirebaseAuthFilter(VerificadorDeToken verificador) {
        this.verificador = Objects.requireNonNull(verificador);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // El preflight de CORS no lleva token
        return HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        UsuarioAutenticado usuario;
        try {
            usuario = verificador.verificar(token(request));
        } catch (NoAutenticadoException e) {
            responderNoAutenticado(response, e.getMessage());
            return;
        }
        request.setAttribute(AtributosDePeticion.USUARIO, usuario);
        chain.doFilter(request, response);
    }

    private static String token(HttpServletRequest request) {
        String encabezado = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (encabezado == null || !encabezado.regionMatches(true, 0, PREFIJO, 0, PREFIJO.length())) {
            throw new NoAutenticadoException("Falta el token de inicio de sesión");
        }
        return encabezado.substring(PREFIJO.length()).strip();
    }

    /** Responde en el mismo formato ProblemDetail que ManejadorDeErrores. Los mensajes son fijos (sin datos del usuario). */
    private static void responderNoAutenticado(HttpServletResponse response, String mensaje) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"title":"No autenticado","status":401,"detail":"%s"}""".formatted(mensaje.replace("\"", "'")));
    }
}
