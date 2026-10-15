package co.edu.uco.jjskafee.movimientos.dominio;

import co.edu.uco.jjskafee.compartido.dominio.mensajes.CodigoMensaje;

/** Claves de los mensajes del módulo movimientos (textos en i18n/mensajes.properties). */
public enum MensajesMovimientos implements CodigoMensaje {

    FECHAS_INVERTIDAS("movimientos.fechas.invertidas"),
    PAGINA_INVALIDA("movimientos.pagina.invalida"),
    TAMANO_INVALIDO("movimientos.tamano.invalido"),
    DESCRIPCION_VENTA("movimientos.descripcion.venta");

    private final String clave;

    MensajesMovimientos(String clave) {
        this.clave = clave;
    }

    @Override
    public String clave() {
        return clave;
    }
}
