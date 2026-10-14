package co.edu.uco.jjskafee.dominio.excepciones;

/** Se lanza cuando el token falta, no es válido o el usuario no está activo en su cafetería (el API responde 401). */
public class NoAutenticadoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NoAutenticadoException(String mensaje) {
        super(mensaje);
    }
}
