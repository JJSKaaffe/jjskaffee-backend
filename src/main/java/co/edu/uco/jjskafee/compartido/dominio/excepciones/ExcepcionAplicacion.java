package co.edu.uco.jjskafee.compartido.dominio.excepciones;

import co.edu.uco.jjskafee.compartido.dominio.mensajes.CodigoMensaje;

import java.util.Objects;

/**
 * Base de todas las excepciones esperadas del sistema.
 * No lleva texto: lleva el código del mensaje y sus argumentos ({0}, {1}...).
 * El ManejadorErrores busca el texto en i18n/mensajes.properties.
 */
public abstract class ExcepcionAplicacion extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient CodigoMensaje codigo;
    private final transient Object[] argumentos;

    protected ExcepcionAplicacion(CodigoMensaje codigo, Object... argumentos) {
        super(Objects.requireNonNull(codigo).clave());
        this.codigo = codigo;
        this.argumentos = argumentos == null ? new Object[0] : argumentos.clone();
    }

    public CodigoMensaje getCodigo() {
        return codigo;
    }

    public Object[] getArgumentos() {
        return argumentos.clone();
    }
}
