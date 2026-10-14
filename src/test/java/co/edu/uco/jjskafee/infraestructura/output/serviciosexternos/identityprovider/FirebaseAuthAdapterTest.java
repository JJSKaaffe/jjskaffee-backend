package co.edu.uco.jjskafee.infraestructura.output.serviciosexternos.identityprovider;

import co.edu.uco.jjskafee.dominio.excepciones.NoAutenticadoException;
import co.edu.uco.jjskafee.infraestructura.output.repositorios.estructurados.PgUsuarioRepository;
import co.edu.uco.jjskafee.servicios.output.ports.UnidadDeTrabajo;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** Usa el SDK real de Firebase (sin red: los tokens mal formados se rechazan antes de descargar llaves). */
class FirebaseAuthAdapterTest {

    private static FirebaseApp app;
    private static FirebaseAuthAdapter adaptador;

    @BeforeAll
    static void iniciarFirebase() {
        app = FirebaseApp.initializeApp(FirebaseOptions.builder()
                .setProjectId("jjskaffee-prueba")
                .setCredentials(GoogleCredentials.create(new AccessToken("prueba", null)))
                .build(), "prueba-adaptador");
        // La BD nunca se toca: si se llegara a usar, la prueba fallaría por la operación sin implementar
        UnidadDeTrabajo sinBaseDeDatos = new UnidadDeTrabajo() {
            @Override
            public <T> T ejecutar(UUID idCafeteria, Supplier<T> operacion) {
                return fail("No debe consultar usuarios con un token inválido");
            }
        };
        adaptador = new FirebaseAuthAdapter(FirebaseAuth.getInstance(app), sinBaseDeDatos,
                new PgUsuarioRepository(new JdbcTemplate()));
    }

    @AfterAll
    static void cerrarFirebase() {
        app.delete();
    }

    @Test
    void tokenVacioONuloEsNoAutenticado() {
        assertThrows(NoAutenticadoException.class, () -> adaptador.verificar(null));
        assertThrows(NoAutenticadoException.class, () -> adaptador.verificar("  "));
    }

    @Test
    void tokenMalFormadoEsNoAutenticado() {
        NoAutenticadoException e = assertThrows(NoAutenticadoException.class, () -> adaptador.verificar("esto-no-es-un-jwt"));
        assertEquals("El token no es válido o ya venció", e.getMessage());
    }

    @Test
    void tokenFirmadoPorOtroLadoEsNoAutenticado() {
        // JWT con algoritmo "none" y claims inventados: Firebase lo rechaza sin consultar la red
        String falso = "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0."
                + "eyJzdWIiOiJ4IiwiaWRfY2FmZXRlcmlhIjoiMTExMTExMTEtMTExMS0xMTExLTExMTEtMTExMTExMTExMTExIn0.";
        assertThrows(NoAutenticadoException.class, () -> adaptador.verificar(falso));
    }
}
