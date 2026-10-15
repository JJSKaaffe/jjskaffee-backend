package co.edu.uco.jjskafee.compartido.dominio.excepciones;

import co.edu.uco.jjskafee.compartido.dominio.mensajes.CodigoMensaje;

/** Falta el token, no es válido o el usuario no está registrado/activo (el API responde 401). */
public class NoAutenticadoException extends ExcepcionAplicacion {

    private static final long serialVersionUID = 1L;

    public NoAutenticadoException(CodigoMensaje codigo, Object... argumentos) {
        super(codigo, argumentos);
    }
}
