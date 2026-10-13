package co.edu.uco.jjskafee.dominio.excepciones;

/** Se lanza cuando no existe el producto, la categoría o la venta pedida (el API responde 404). */
public class RecursoNoEncontradoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
