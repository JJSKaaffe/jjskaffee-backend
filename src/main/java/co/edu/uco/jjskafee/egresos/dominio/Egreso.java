package co.edu.uco.jjskafee.egresos.dominio;

import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.ReglaNegocioException;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.SinPermisoException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un gasto de la cafetería (RF14, tabla egreso). Reglas: solo lo registra el administrador,
 * el monto es mayor que 0 y la categoría debe ser de egreso y estar activa (RF18).
 */
public final class Egreso {

    /** Iguales al tamaño de las columnas egreso.descripcion y egreso.comprobante. */
    public static final int MAX_DESCRIPCION = 200;
    public static final int MAX_COMPROBANTE = 50;

    private final UUID id;
    private final UUID idCafeteria;
    private final CategoriaEgreso categoria;
    private final BigDecimal monto;
    private final String descripcion;
    private final String comprobante;
    private final UUID registradoPor;
    private final Instant fechaHora;

    private Egreso(UUID id, UUID idCafeteria, CategoriaEgreso categoria, BigDecimal monto, String descripcion,
                   String comprobante, UUID registradoPor, Instant fechaHora) {
        this.id = id;
        this.idCafeteria = idCafeteria;
        this.categoria = categoria;
        this.monto = monto;
        this.descripcion = descripcion;
        this.comprobante = comprobante;
        this.registradoPor = registradoPor;
        this.fechaHora = fechaHora;
    }

    /**
     * Crea un egreso nuevo validando todas las reglas.
     * La cafetería, el usuario y la fecha los pone el servidor, nunca el navegador (ADR-0007).
     */
    public static Egreso registrar(UsuarioAutenticado usuario, CategoriaEgreso categoria, BigDecimal monto,
                                   String descripcion, String comprobante, Instant ahora) {
        Objects.requireNonNull(usuario);
        Objects.requireNonNull(ahora);

        if (!usuario.esAdministrador()) {
            throw new SinPermisoException(MensajesEgresos.SOLO_ADMINISTRADOR);
        }
        if (categoria == null || !categoria.esEgresoActiva()) {
            throw new ReglaNegocioException(MensajesEgresos.CATEGORIA_INVALIDA);
        }
        if (monto == null || monto.signum() <= 0) {
            throw new ReglaNegocioException(MensajesEgresos.MONTO_INVALIDO);
        }
        String descripcionLimpia = descripcion == null ? "" : descripcion.strip();
        if (descripcionLimpia.isEmpty() || descripcionLimpia.length() > MAX_DESCRIPCION) {
            throw new ReglaNegocioException(MensajesEgresos.DESCRIPCION_INVALIDA, MAX_DESCRIPCION);
        }
        String comprobanteLimpio = (comprobante == null || comprobante.isBlank()) ? null : comprobante.strip();
        if (comprobanteLimpio != null && comprobanteLimpio.length() > MAX_COMPROBANTE) {
            throw new ReglaNegocioException(MensajesEgresos.COMPROBANTE_INVALIDO, MAX_COMPROBANTE);
        }

        return new Egreso(UUID.randomUUID(), usuario.idCafeteria(), categoria, monto, descripcionLimpia,
                comprobanteLimpio, usuario.id(), ahora);
    }

    public UUID getId() { return id; }
    public UUID getIdCafeteria() { return idCafeteria; }
    public CategoriaEgreso getCategoria() { return categoria; }
    public BigDecimal getMonto() { return monto; }
    public String getDescripcion() { return descripcion; }
    public String getComprobante() { return comprobante; }
    public UUID getRegistradoPor() { return registradoPor; }
    public Instant getFechaHora() { return fechaHora; }
}
