package co.edu.uco.jjskafee.infraestructura.output.serviciosexternos.identityprovider;

import co.edu.uco.jjskafee.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.dominio.excepciones.NoAutenticadoException;
import co.edu.uco.jjskafee.infraestructura.output.repositorios.estructurados.PgUsuarioRepository;
import co.edu.uco.jjskafee.servicios.output.ports.UnidadDeTrabajo;
import co.edu.uco.jjskafee.servicios.output.ports.VerificadorDeToken;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;

import java.util.Objects;
import java.util.UUID;

/**
 * Verifica el token de Firebase Authentication (ADR-0001) y arma el UsuarioAutenticado:
 * la cafetería sale del custom claim id_cafeteria y el id, nombre y rol de la tabla usuarios,
 * leída con RLS de esa cafetería. Así un cambio de rol o una desactivación aplica de inmediato.
 */
public class FirebaseAuthAdapter implements VerificadorDeToken {

    static final String CLAIM_CAFETERIA = "id_cafeteria";

    private final FirebaseAuth firebaseAuth;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final PgUsuarioRepository usuarios;

    public FirebaseAuthAdapter(FirebaseAuth firebaseAuth, UnidadDeTrabajo unidadDeTrabajo, PgUsuarioRepository usuarios) {
        this.firebaseAuth = Objects.requireNonNull(firebaseAuth);
        this.unidadDeTrabajo = Objects.requireNonNull(unidadDeTrabajo);
        this.usuarios = Objects.requireNonNull(usuarios);
    }

    @Override
    public UsuarioAutenticado verificar(String token) {
        if (token == null || token.isBlank()) {
            throw new NoAutenticadoException("Falta el token de inicio de sesión");
        }
        FirebaseToken verificado;
        try {
            verificado = firebaseAuth.verifyIdToken(token);
        } catch (FirebaseAuthException | IllegalArgumentException e) {
            throw new NoAutenticadoException("El token no es válido o ya venció");
        }

        UUID idCafeteria = cafeteriaDelToken(verificado);
        return unidadDeTrabajo.ejecutar(idCafeteria, () -> usuarios.buscarActivoPorFirebaseUid(verificado.getUid()))
                .orElseThrow(() -> new NoAutenticadoException("El usuario no está activo en la cafetería"));
    }

    private static UUID cafeteriaDelToken(FirebaseToken token) {
        Object claim = token.getClaims().get(CLAIM_CAFETERIA);
        if (claim == null) {
            throw new NoAutenticadoException("El token no tiene la cafetería del usuario");
        }
        try {
            return UUID.fromString(claim.toString());
        } catch (IllegalArgumentException e) {
            throw new NoAutenticadoException("La cafetería del token no es válida");
        }
    }
}
