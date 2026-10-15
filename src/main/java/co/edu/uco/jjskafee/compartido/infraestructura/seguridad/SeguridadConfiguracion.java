package co.edu.uco.jjskafee.compartido.infraestructura.seguridad;

import co.edu.uco.jjskafee.compartido.aplicacion.AutenticarUsuario;
import co.edu.uco.jjskafee.compartido.aplicacion.puertos.ProveedorMensajes;
import co.edu.uco.jjskafee.compartido.aplicacion.puertos.VerificadorDeToken;
import co.edu.uco.jjskafee.compartido.dominio.mensajes.MensajesCompartidos;
import co.edu.uco.jjskafee.compartido.infraestructura.configuracion.PropiedadesJjskaffee;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.List;

/**
 * Seguridad del API: verificador de tokens, CORS para el frontend Vue y filtro de autenticación.
 * Orden de filtros: trazas OTel (lo pone Spring) → CORS → autenticación → controladores.
 */
@Configuration(proxyBeanMethods = false)
public class SeguridadConfiguracion implements WebMvcConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(SeguridadConfiguracion.class);
    private static final String RUTAS_API = "/api/*";
    private static final int ORDEN_CORS = Ordered.HIGHEST_PRECEDENCE + 10;
    private static final int ORDEN_AUTENTICACION = Ordered.HIGHEST_PRECEDENCE + 20;

    /** Producción y nube: tokens reales de Firebase. Credenciales por Application Default Credentials. */
    @Bean
    @ConditionalOnProperty(name = "jjskaffee.seguridad.modo", havingValue = "firebase", matchIfMissing = true)
    VerificadorDeToken verificadorFirebase(PropiedadesJjskaffee propiedades) throws IOException {
        FirebaseApp app = FirebaseApp.getApps().isEmpty()
                ? FirebaseApp.initializeApp(FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.getApplicationDefault())
                        .setProjectId(propiedades.seguridad().proyectoFirebase())
                        .build())
                : FirebaseApp.getInstance();
        return new VerificadorFirebase(FirebaseAuth.getInstance(app), propiedades.seguridad().claimCafeteria());
    }

    /** Solo en tu PC: perfil "local" Y modo "desarrollo". En cualquier otro perfil no existe este bean. */
    @Bean
    @Profile("local")
    @ConditionalOnProperty(name = "jjskaffee.seguridad.modo", havingValue = "desarrollo")
    VerificadorDeToken verificadorDesarrollo(ProveedorMensajes mensajes) {
        LOG.warn(mensajes.obtener(MensajesCompartidos.AVISO_MODO_DESARROLLO));
        return new VerificadorDesarrollo();
    }

    @Bean
    FilterRegistrationBean<CorsFilter> filtroCors(PropiedadesJjskaffee propiedades) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(propiedades.cors().origenes());
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource origen = new UrlBasedCorsConfigurationSource();
        origen.registerCorsConfiguration("/api/**", cors);

        FilterRegistrationBean<CorsFilter> registro = new FilterRegistrationBean<>(new CorsFilter(origen));
        registro.addUrlPatterns(RUTAS_API);
        registro.setOrder(ORDEN_CORS);
        return registro;
    }

    @Bean
    FilterRegistrationBean<FiltroAutenticacion> filtroAutenticacion(
            AutenticarUsuario autenticarUsuario,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolutorErrores) {
        FilterRegistrationBean<FiltroAutenticacion> registro =
                new FilterRegistrationBean<>(new FiltroAutenticacion(autenticarUsuario, resolutorErrores));
        registro.addUrlPatterns(RUTAS_API);
        registro.setOrder(ORDEN_AUTENTICACION);
        return registro;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolutores) {
        resolutores.add(new ResolutorUsuarioAutenticado());
    }
}
