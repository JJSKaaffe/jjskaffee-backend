package co.edu.uco.jjskafee.compartido.aplicacion;

import co.edu.uco.jjskafee.compartido.aplicacion.puertos.RepositorioUsuarios;
import co.edu.uco.jjskafee.compartido.aplicacion.puertos.UnidadDeTrabajo;
import co.edu.uco.jjskafee.compartido.aplicacion.puertos.VerificadorDeToken;
import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.NoAutenticadoException;
import co.edu.uco.jjskafee.compartido.dominio.mensajes.MensajesCompartidos;

import java.util.Objects;

/**
 * Caso de uso: convertir un token en un UsuarioAutenticado.
 * 1) el verificador valida el token y entrega uid + cafetería;
 * 2) se busca el usuario activo de esa cafetería (con RLS activado).
 * Java puro: lo crea CompartidoConfiguracion.
 */
public class AutenticarUsuario {

    private final VerificadorDeToken verificador;
    private final RepositorioUsuarios usuarios;
    private final UnidadDeTrabajo unidadDeTrabajo;

    public AutenticarUsuario(VerificadorDeToken verificador, RepositorioUsuarios usuarios,
                             UnidadDeTrabajo unidadDeTrabajo) {
        this.verificador = Objects.requireNonNull(verificador);
        this.usuarios = Objects.requireNonNull(usuarios);
        this.unidadDeTrabajo = Objects.requireNonNull(unidadDeTrabajo);
    }

    public UsuarioAutenticado autenticar(String token) {
        if (token == null || token.isBlank()) {
            throw new NoAutenticadoException(MensajesCompartidos.AUTENTICACION_REQUERIDA);
        }
        IdentidadVerificada identidad = verificador.verificar(token);
        return unidadDeTrabajo.ejecutar(identidad.idCafeteria(),
                        () -> usuarios.buscarActivoPorUid(identidad.idCafeteria(), identidad.firebaseUid()))
                .orElseThrow(() -> new NoAutenticadoException(MensajesCompartidos.USUARIO_NO_REGISTRADO));
    }
}
