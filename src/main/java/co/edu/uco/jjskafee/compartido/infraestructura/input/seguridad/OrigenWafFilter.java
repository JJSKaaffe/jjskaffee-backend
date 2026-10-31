package co.edu.uco.jjskafee.compartido.infraestructura.input.seguridad;

import co.edu.uco.jjskafee.compartido.infraestructura.input.Encabezados;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Rechaza lo que no pasó por el balanceador con Cloud Armor (ADR-0018). La URL *.gateway.dev del API Gateway
 * no se puede apagar; el balanceador agrega X-JJSKaffee-Origen con un valor secreto y aquí se exige.
 * Con jjskaffee.waf.secreto vacío (local y pruebas) no hace nada. Aplica a todo, incluido el preflight
 * (OPTIONS), y corre antes que la autenticación: sin el encabezado es 403 aunque falte el token.
 */
@Component
@Order(OrigenWafFilter.ORDEN)
public class OrigenWafFilter extends OncePerRequestFilter {

    /** Antes que cualquier filtro de la aplicación (FirebaseAuthFilter no tiene orden: va de último). */
    public static final int ORDEN = Ordered.HIGHEST_PRECEDENCE + 10;

    private final byte[] secreto;

    public OrigenWafFilter(@Value("${jjskaffee.waf.secreto:}") String secreto) {
        this.secreto = secreto == null ? new byte[0] : secreto.strip().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return secreto.length == 0;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origen = request.getHeader(Encabezados.ORIGEN_WAF);
        // Comparación en tiempo constante: no revela cuántos caracteres coinciden
        if (origen == null || !MessageDigest.isEqual(secreto, origen.getBytes(StandardCharsets.UTF_8))) {
            responderSinPermiso(response);
            return;
        }
        chain.doFilter(request, response);
    }

    /** Mismo formato ProblemDetail que ManejadorDeErrores; el mensaje no dice si faltó el encabezado o si no coincidió. */
    private static void responderSinPermiso(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"title":"Sin permiso","status":403,"detail":"Acceso no permitido"}""");
    }
}
