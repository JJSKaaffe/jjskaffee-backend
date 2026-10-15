package co.edu.uco.jjskafee.compartido.dominio.excepciones;

import co.edu.uco.jjskafee.compartido.dominio.mensajes.CodigoMensaje;

/** El usuario está autenticado pero su rol no le permite la operación (el API responde 403). */
public class SinPermisoException extends ExcepcionAplicacion {

    private static final long serialVersionUID = 1L;

    public SinPermisoException(CodigoMensaje codigo, Object... argumentos) {
        super(codigo, argumentos);
    }
}
