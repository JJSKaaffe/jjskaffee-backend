package co.edu.uco.jjskafee.compartido.dominio.mensajes;

/**
 * Un mensaje del sistema identificado por su clave.
 * El texto NUNCA va en el código: vive en src/main/resources/i18n/mensajes.properties.
 * Cada módulo tiene su propio enum que implementa esta interfaz.
 */
public interface CodigoMensaje {

    /** Clave en mensajes.properties, por ejemplo "egresos.monto.invalido". */
    String clave();
}
