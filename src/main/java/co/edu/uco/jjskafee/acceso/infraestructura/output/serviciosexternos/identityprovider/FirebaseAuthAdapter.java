package co.edu.uco.jjskafee.acceso.infraestructura.output.serviciosexternos.identityprovider;

import co.edu.uco.jjskafee.acceso.dominio.Identidad;
import co.edu.uco.jjskafee.acceso.servicios.output.ports.VerificadorDeToken;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.NoAutenticadoException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;

import java.util.Objects;

/**
 * Verifica el token de Firebase Authentication (ADR-0001) y devuelve solo la identidad (uid).
 * El token ya no trae la cafetería: se elige con el encabezado X-Cafeteria-Id y el rol sale
 * de la membresía en esa cafetería (ADR-0016).
 */
public class FirebaseAuthAdapter implements VerificadorDeToken {

    private final FirebaseAuth firebaseAuth;

    public FirebaseAuthAdapter(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = Objects.requireNonNull(firebaseAuth);
    }

    @Override
    public Identidad verificar(String token) {
        if (token == null || token.isBlank()) {
            throw new NoAutenticadoException("Falta el token de inicio de sesión");
        }
        try {
            return new Identidad(firebaseAuth.verifyIdToken(token).getUid());
        } catch (FirebaseAuthException | IllegalArgumentException e) {
            throw new NoAutenticadoException("El token no es válido o ya venció");
        }
    }
}
