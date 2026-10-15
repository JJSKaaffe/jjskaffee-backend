package co.edu.uco.jjskafee.compartido.infraestructura.seguridad;

import co.edu.uco.jjskafee.compartido.aplicacion.IdentidadVerificada;
import co.edu.uco.jjskafee.compartido.aplicacion.puertos.VerificadorDeToken;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.NoAutenticadoException;
import co.edu.uco.jjskafee.compartido.dominio.mensajes.MensajesCompartidos;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;

import java.util.Objects;
import java.util.UUID;

/**
 * Verifica el ID token de Firebase Authentication (ADR-0001, SPIKE-01).
 * La cafetería viene en un custom claim que pone el módulo externo de usuarios.
 */
public class VerificadorFirebase implements VerificadorDeToken {

    private final FirebaseAuth firebase;
    private final String claimCafeteria;

    public VerificadorFirebase(FirebaseAuth firebase, String claimCafeteria) {
        this.firebase = Objects.requireNonNull(firebase);
        this.claimCafeteria = Objects.requireNonNull(claimCafeteria);
    }

    @Override
    public IdentidadVerificada verificar(String token) {
        FirebaseToken verificado;
        try {
            verificado = firebase.verifyIdToken(token);
        } catch (FirebaseAuthException | IllegalArgumentException e) {
            throw new NoAutenticadoException(MensajesCompartidos.TOKEN_INVALIDO);
        }
        Object cafeteria = verificado.getClaims().get(claimCafeteria);
        if (cafeteria == null) {
            throw new NoAutenticadoException(MensajesCompartidos.TOKEN_SIN_CAFETERIA);
        }
        try {
            return new IdentidadVerificada(verificado.getUid(), UUID.fromString(cafeteria.toString()));
        } catch (IllegalArgumentException e) {
            throw new NoAutenticadoException(MensajesCompartidos.TOKEN_SIN_CAFETERIA);
        }
    }
}
