package co.edu.uco.jjskafee.compartido.infraestructura.input;

/** Encabezados HTTP propios de la API (los usan los filtros, los controllers y la configuración de CORS). */
public final class Encabezados {

    /** Cafetería elegida por el usuario; va en toda petición salvo GET /acceso/cafeterias (ADR-0016). */
    public static final String CAFETERIA = "X-Cafeteria-Id";

    /** Clave (UUID) para que un reintento del mismo registro no lo duplique. */
    public static final String IDEMPOTENCIA = "Idempotency-Key";

    private Encabezados() { }
}
