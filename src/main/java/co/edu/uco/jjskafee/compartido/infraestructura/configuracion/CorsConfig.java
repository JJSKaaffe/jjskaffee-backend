package co.edu.uco.jjskafee.compartido.infraestructura.configuracion;

import co.edu.uco.jjskafee.compartido.infraestructura.input.Encabezados;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Orígenes del frontend que pueden llamar la API (Vite en local; en la nube, el del frontend publicado).
 * Detrás del API Gateway el preflight (OPTIONS) llega sin token hasta aquí (ADR-0017). Vacío = ninguno.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] origenes;

    public CorsConfig(@Value("${jjskaffee.cors.origenes:}") String[] origenes) {
        this.origenes = origenes;
    }

    @Override
    public void addCorsMappings(CorsRegistry registro) {
        if (origenes.length > 0) {
            registro.addMapping("/**")
                    .allowedOrigins(origenes)
                    .allowedMethods("GET", "POST")
                    .allowedHeaders("Authorization", "Content-Type", Encabezados.IDEMPOTENCIA, Encabezados.CAFETERIA);
        }
    }
}
