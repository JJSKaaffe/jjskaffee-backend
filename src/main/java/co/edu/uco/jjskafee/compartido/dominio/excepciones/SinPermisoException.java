package co.edu.uco.jjskafee.compartido.dominio.excepciones;

/** Se lanza cuando el usuario no tiene el rol necesario (el API responde 403). */
public class SinPermisoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SinPermisoException(String mensaje) {
        super(mensaje);
    }
}
