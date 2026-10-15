package co.edu.uco.jjskafee.ingresos.dominio;

import co.edu.uco.jjskafee.compartido.dominio.mensajes.CodigoMensaje;

/** Claves de los mensajes del módulo ingresos (textos en i18n/mensajes.properties). */
public enum MensajesIngresos implements CodigoMensaje {

    PRODUCTO_NO_EXISTE("ingresos.producto.no-existe"),
    PRODUCTO_ES_ADICION("ingresos.producto.es-adicion"),
    PRODUCTO_NO_DISPONIBLE("ingresos.producto.no-disponible"),
    CANTIDAD_MINIMA("ingresos.cantidad.minima"),
    ADICION_NO_PERMITIDA("ingresos.adicion.no-permitida"),
    CANTIDAD_ADICION_MINIMA("ingresos.adicion.cantidad-minima"),
    VENTA_SIN_PRODUCTOS("ingresos.venta.sin-productos"),
    VENTA_NO_EXISTE("ingresos.venta.no-existe"),
    VENTA_YA_ANULADA("ingresos.venta.ya-anulada"),
    SOLO_QUIEN_REGISTRO_DESHACE("ingresos.deshacer.solo-quien-registro"),
    TIEMPO_DESHACER_VENCIDO("ingresos.deshacer.tiempo-vencido"),
    MOTIVO_DESHACER("ingresos.deshacer.motivo");

    private final String clave;

    MensajesIngresos(String clave) {
        this.clave = clave;
    }

    @Override
    public String clave() {
        return clave;
    }
}
