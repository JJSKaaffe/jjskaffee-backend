package co.edu.uco.jjskafee.egresos.dominio;

import co.edu.uco.jjskafee.compartido.dominio.mensajes.CodigoMensaje;

/** Claves de los mensajes del módulo egresos (textos en i18n/mensajes.properties). */
public enum MensajesEgresos implements CodigoMensaje {

    SOLO_ADMINISTRADOR("egresos.solo-administrador"),
    CATEGORIA_NO_EXISTE("egresos.categoria.no-existe"),
    CATEGORIA_INVALIDA("egresos.categoria.invalida"),
    MONTO_INVALIDO("egresos.monto.invalido"),
    DESCRIPCION_INVALIDA("egresos.descripcion.invalida"),
    COMPROBANTE_INVALIDO("egresos.comprobante.invalido");

    private final String clave;

    MensajesEgresos(String clave) {
        this.clave = clave;
    }

    @Override
    public String clave() {
        return clave;
    }
}
