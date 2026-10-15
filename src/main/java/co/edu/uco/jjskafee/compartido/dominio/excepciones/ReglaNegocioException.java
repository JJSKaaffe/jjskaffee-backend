package co.edu.uco.jjskafee.compartido.dominio.excepciones;

import co.edu.uco.jjskafee.compartido.dominio.mensajes.CodigoMensaje;

/** Una operación viola una regla del negocio (el API responde 422). */
public class ReglaNegocioException extends ExcepcionAplicacion {

    private static final long serialVersionUID = 1L;

    public ReglaNegocioException(CodigoMensaje codigo, Object... argumentos) {
        super(codigo, argumentos);
    }
}
