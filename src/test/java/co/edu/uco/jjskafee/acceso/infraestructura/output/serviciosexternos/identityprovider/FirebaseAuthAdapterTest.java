package co.edu.uco.jjskafee.acceso.infraestructura.output.serviciosexternos.identityprovider;

import co.edu.uco.jjskafee.compartido.dominio.excepciones.NoAutenticadoException;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
        // El adaptador ya no consulta la BD: solo verifica el token y devuelve el uid (ADR-0016)
        adaptador = new FirebaseAuthAdapter(FirebaseAuth.getInstance(app));
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
