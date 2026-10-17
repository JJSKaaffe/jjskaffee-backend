package co.edu.uco.jjskafee.dominio;

import co.edu.uco.jjskafee.dominio.excepciones.ReglaNegocioException;
import co.edu.uco.jjskafee.dominio.excepciones.SinPermisoException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un gasto de la cafetería (RF14). Contiene sus propias reglas:
 * solo lo registra el administrador, el monto es mayor que 0 y
 * la categoría debe ser de egreso y estar activa (RF18).
 */
public class Egreso {

    public static final int MAX_DESCRIPCION = 200;
    public static final int MAX_COMPROBANTE = 50;

    private final UUID id;
    private final UUID idCafeteria;
    private final Categoria categoria;
    private final BigDecimal monto;
    private final String descripcion;
    private final String comprobante;
    private final UUID registradoPor;
    private final Instant fechaHora;

    private Egreso(UUID id, UUID idCafeteria, Categoria categoria, BigDecimal monto, String descripcion,
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
     * Crea un egreso nuevo validando todas las reglas del negocio.
     * La cafetería, el usuario y la fecha los pone el servidor, nunca el navegador (ADR-0007).
     */
    public static Egreso registrar(UsuarioAutenticado usuario, Categoria categoria, BigDecimal monto,
                                   String descripcion, String comprobante, Instant ahora) {
        Objects.requireNonNull(usuario, "El usuario es obligatorio");
        Objects.requireNonNull(ahora, "La fecha es obligatoria");

        if (!usuario.esAdministrador()) {
            throw new SinPermisoException("Solo el administrador puede registrar egresos");
        }
        if (categoria == null || !categoria.esEgresoActiva()) {
            throw new ReglaNegocioException("La categoría debe ser de egreso y estar activa");
        }
        if (monto == null || monto.signum() <= 0) {
            throw new ReglaNegocioException("El monto debe ser mayor que 0");
        }
        String descripcionLimpia = descripcion == null ? "" : descripcion.strip();
        if (descripcionLimpia.isEmpty() || descripcionLimpia.length() > MAX_DESCRIPCION) {
            throw new ReglaNegocioException("La descripción es obligatoria y tiene máximo " + MAX_DESCRIPCION + " caracteres");
        }
        String comprobanteLimpio = (comprobante == null || comprobante.isBlank()) ? null : comprobante.strip();
        if (comprobanteLimpio != null && comprobanteLimpio.length() > MAX_COMPROBANTE) {
            throw new ReglaNegocioException("El comprobante tiene máximo " + MAX_COMPROBANTE + " caracteres");
        }

        return new Egreso(UUID.randomUUID(), usuario.idCafeteria(), categoria, monto, descripcionLimpia,
                comprobanteLimpio, usuario.id(), ahora);
    }

    /** Vuelve a armar un egreso que ya estaba guardado (lo usa el repositorio al leer la BD). */
    public static Egreso reconstruir(UUID id, UUID idCafeteria, Categoria categoria, BigDecimal monto,
                                     String descripcion, String comprobante, UUID registradoPor, Instant fechaHora) {
        return new Egreso(id, idCafeteria, categoria, monto, descripcion, comprobante, registradoPor, fechaHora);
    }

    public UUID getId() { return id; }
    public UUID getIdCafeteria() { return idCafeteria; }
    public Categoria getCategoria() { return categoria; }
    public BigDecimal getMonto() { return monto; }
    public String getDescripcion() { return descripcion; }
    public String getComprobante() { return comprobante; }
    public UUID getRegistradoPor() { return registradoPor; }
    public Instant getFechaHora() { return fechaHora; }
}
