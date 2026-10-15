package co.edu.uco.jjskafee.compartido.dominio.mensajes;

/** Claves de los mensajes del módulo compartido (textos en i18n/mensajes.properties). */
public enum MensajesCompartidos implements CodigoMensaje {

    AUTENTICACION_REQUERIDA("compartido.autenticacion.requerida"),
    TOKEN_INVALIDO("compartido.autenticacion.token-invalido"),
    TOKEN_SIN_CAFETERIA("compartido.autenticacion.token-sin-cafeteria"),
    USUARIO_NO_REGISTRADO("compartido.autenticacion.usuario-no-registrado"),
    SOLICITUD_INVALIDA("compartido.solicitud.invalida"),
    CUERPO_ILEGIBLE("compartido.solicitud.cuerpo-ilegible"),
    PARAMETRO_INVALIDO("compartido.solicitud.parametro-invalido"),
    RUTA_NO_EXISTE("compartido.solicitud.ruta-no-existe"),
    METODO_NO_PERMITIDO("compartido.solicitud.metodo-no-permitido"),
    DATOS_INCONSISTENTES("compartido.datos.inconsistentes"),
    BASE_DATOS_NO_DISPONIBLE("compartido.datos.no-disponible"),
    CAFETERIA_DISTINTA_EN_TRANSACCION("compartido.datos.cafeteria-distinta"),
    ERROR_INESPERADO("compartido.error.inesperado"),
    AVISO_MODO_DESARROLLO("compartido.seguridad.aviso-modo-desarrollo"),
    TITULO_ERROR_SOLICITUD("compartido.titulo.solicitud"),
    TITULO_NO_AUTENTICADO("compartido.titulo.no-autenticado"),
    TITULO_SIN_PERMISO("compartido.titulo.sin-permiso"),
    TITULO_NO_ENCONTRADO("compartido.titulo.no-encontrado"),
    TITULO_REGLA_NEGOCIO("compartido.titulo.regla-negocio"),
    TITULO_NO_DISPONIBLE("compartido.titulo.no-disponible"),
    TITULO_ERROR_INTERNO("compartido.titulo.error-interno");

    private final String clave;

    MensajesCompartidos(String clave) {
        this.clave = clave;
    }

    @Override
    public String clave() {
        return clave;
    }
}
