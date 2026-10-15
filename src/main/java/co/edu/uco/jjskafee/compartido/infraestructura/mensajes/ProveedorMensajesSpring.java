package co.edu.uco.jjskafee.compartido.infraestructura.mensajes;

import co.edu.uco.jjskafee.compartido.aplicacion.puertos.ProveedorMensajes;
import co.edu.uco.jjskafee.compartido.dominio.mensajes.CodigoMensaje;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Objects;

/**
 * Busca los textos en i18n/mensajes.properties (spring.messages.basename).
 * Si una clave no existe devuelve la misma clave, para que se note en pantalla;
 * MensajesTest falla antes de que eso llegue a producción.
 */
public class ProveedorMensajesSpring implements ProveedorMensajes {

    private final MessageSource mensajes;

    public ProveedorMensajesSpring(MessageSource mensajes) {
        this.mensajes = Objects.requireNonNull(mensajes);
    }

    @Override
    public String obtener(CodigoMensaje codigo, Object... argumentos) {
        return mensajes.getMessage(codigo.clave(), argumentos, codigo.clave(), LocaleContextHolder.getLocale());
    }
}
