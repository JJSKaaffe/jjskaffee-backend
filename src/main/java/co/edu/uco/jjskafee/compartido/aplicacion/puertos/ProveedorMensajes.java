package co.edu.uco.jjskafee.compartido.aplicacion.puertos;

import co.edu.uco.jjskafee.compartido.dominio.mensajes.CodigoMensaje;

/**
 * Devuelve el texto de un mensaje a partir de su código.
 * Los textos están en i18n/mensajes.properties; aquí no hay ningún texto escrito.
 */
public interface ProveedorMensajes {

    String obtener(CodigoMensaje codigo, Object... argumentos);
}
