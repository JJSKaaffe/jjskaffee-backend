package co.edu.uco.jjskafee.dominio.excepciones;

/** Se lanza cuando una operación viola una regla del negocio (el API responde 422). */
public class ReglaNegocioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
