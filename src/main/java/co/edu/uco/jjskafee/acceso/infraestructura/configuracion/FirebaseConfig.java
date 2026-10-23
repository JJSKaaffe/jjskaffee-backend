package co.edu.uco.jjskafee.acceso.infraestructura.configuracion;

import co.edu.uco.jjskafee.acceso.infraestructura.output.repositorios.estructurados.PgUsuarioRepository;
import co.edu.uco.jjskafee.acceso.infraestructura.output.serviciosexternos.identityprovider.FirebaseAuthAdapter;
import co.edu.uco.jjskafee.acceso.servicios.output.ports.VerificadorDeToken;
import co.edu.uco.jjskafee.compartido.servicios.output.ports.UnidadDeTrabajo;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Conecta con Firebase Authentication para verificar tokens (ADR-0001).
 * Credenciales:
 *  - local: archivo JSON de la cuenta de servicio (ruta en JJSKAFFEE_FIREBASE_CREDENCIALES, fuera del repo)
 *    o, si está vacío, las credenciales por defecto de Google (gcloud auth application-default login).
 *  - Cloud Run: las credenciales por defecto de la cuenta de servicio del servicio.
 * Si falta algo, la aplicación no arranca (mejor fallar al inicio que aceptar peticiones sin verificar).
 */
@Configuration
@ConditionalOnProperty(name = "jjskaffee.firebase.habilitado", havingValue = "true", matchIfMissing = true)
public class FirebaseConfig {

    private static final String NOMBRE_APP = "jjskaffee";

    @Bean
    public FirebaseAuth firebaseAuth(@Value("${jjskaffee.firebase.project-id}") String projectId,
                                     @Value("${jjskaffee.firebase.credenciales:}") String rutaCredenciales)
            throws IOException {
        if (projectId.isBlank()) {
            throw new IllegalStateException("Falta JJSKAFFEE_FIREBASE_PROJECT_ID (id del proyecto de Firebase)");
        }
        FirebaseOptions opciones = FirebaseOptions.builder()
                .setProjectId(projectId)
                .setCredentials(credenciales(rutaCredenciales))
                .build();
        FirebaseApp app = FirebaseApp.getApps().stream()
                .filter(a -> a.getName().equals(NOMBRE_APP))
                .findFirst()
                .orElseGet(() -> FirebaseApp.initializeApp(opciones, NOMBRE_APP));
        return FirebaseAuth.getInstance(app);
    }

    @Bean
    public VerificadorDeToken verificadorDeToken(FirebaseAuth firebaseAuth, UnidadDeTrabajo unidadDeTrabajo,
                                                 JdbcTemplate jdbc) {
        return new FirebaseAuthAdapter(firebaseAuth, unidadDeTrabajo, new PgUsuarioRepository(jdbc));
    }

    private static GoogleCredentials credenciales(String ruta) throws IOException {
        if (ruta.isBlank()) {
            try {
                return GoogleCredentials.getApplicationDefault();
            } catch (IOException e) {
                throw new IllegalStateException("No hay credenciales de Google: define JJSKAFFEE_FIREBASE_CREDENCIALES "
                        + "con la ruta del JSON de la cuenta de servicio o ejecuta 'gcloud auth application-default login'", e);
            }
        }
        try (InputStream json = new FileInputStream(ruta)) {
            return GoogleCredentials.fromStream(json);
        }
    }
}
