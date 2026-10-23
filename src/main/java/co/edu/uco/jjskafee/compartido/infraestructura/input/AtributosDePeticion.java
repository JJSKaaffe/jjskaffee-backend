package co.edu.uco.jjskafee.compartido.infraestructura.input;

/**
 * Nombres de los atributos que los filtros dejan en la petición HTTP. Están en el kernel compartido
 * para que los controllers de cada módulo lean el usuario sin depender de clases internas de acceso.
 */
public final class AtributosDePeticion {

    /** Atributo donde FirebaseAuthFilter deja el UsuarioAutenticado (lo leen los controllers). */
    public static final String USUARIO = "jjskaffee.usuarioAutenticado";

    private AtributosDePeticion() { }
}
