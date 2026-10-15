package co.edu.uco.jjskafee.compartido.dominio.excepciones;

import co.edu.uco.jjskafee.compartido.dominio.mensajes.CodigoMensaje;

/** No existe (o pertenece a otra cafetería, que para el usuario es lo mismo): el API responde 404. */
public class RecursoNoEncontradoException extends ExcepcionAplicacion {

    private static final long serialVersionUID = 1L;

    public RecursoNoEncontradoException(CodigoMensaje codigo, Object... argumentos) {
        super(codigo, argumentos);
    }
}
