package co.edu.uco.jjskafee.ingresos.dominio;

import co.edu.uco.jjskafee.compartido.dominio.Categoria;
import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.compartido.dominio.enums.Permiso;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.ReglaNegocioException;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.SinPermisoException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un ingreso que no sale de vender productos del menú: monto + categoría de ingreso (p. ej. un evento
 * privado). Un ingreso es una venta o un ingreso libre; este solo lo registra el administrador.
 * El monto se valida tal como llega, sin redondear: máximo 10 dígitos enteros y 2 decimales (numeric(12,2)).
 * Nunca se borra: se anula con motivo (borrado lógico).
 */
public class IngresoLibre {

    public static final int MAX_DESCRIPCION = 200;
    public static final int MAX_COMPROBANTE = 50;
    public static final int DECIMALES_DINERO = 2;
    public static final int DIGITOS_ENTEROS = 10;
    /** 10^10: el primer valor que ya tiene 11 dígitos enteros. */
    private static final BigDecimal LIMITE_MONTO = BigDecimal.TEN.pow(DIGITOS_ENTEROS);

    private final UUID id;
    private final UUID idCafeteria;
    private final Categoria categoria;
    private final BigDecimal monto;
    private final String descripcion;
    private final String comprobante;
    private final UUID registradoPor;
    private final Instant fechaHora;
    private final UUID claveIdempotencia;   // null si el frontend no mandó Idempotency-Key
    private final String hashSolicitud;     // huella de lo pedido; null si no hay clave
    private final boolean anulado;
    private final String motivoAnulacion;

    private IngresoLibre(UUID id, UUID idCafeteria, Categoria categoria, BigDecimal monto, String descripcion,
                         String comprobante, UUID registradoPor, Instant fechaHora, UUID claveIdempotencia,
                         String hashSolicitud, boolean anulado, String motivoAnulacion) {
        this.id = id;
        this.idCafeteria = idCafeteria;
        this.categoria = categoria;
        this.monto = monto;
        this.descripcion = descripcion;
        this.comprobante = comprobante;
        this.registradoPor = registradoPor;
        this.fechaHora = fechaHora;
        this.claveIdempotencia = claveIdempotencia;
        this.hashSolicitud = hashSolicitud;
        this.anulado = anulado;
        this.motivoAnulacion = motivoAnulacion;
    }

    /** Registra un ingreso libre sin clave de idempotencia. */
    public static IngresoLibre registrar(UsuarioAutenticado usuario, Categoria categoria, BigDecimal monto,
                                         String descripcion, String comprobante, Instant ahora) {
        return registrar(usuario, categoria, monto, descripcion, comprobante, null, null, ahora);
    }

    /**
     * Crea un ingreso libre nuevo validando todas las reglas del negocio. La cafetería, el usuario y la fecha
     * los pone el servidor, nunca el navegador (ADR-0007). La clave Idempotency-Key y la huella de la
     * solicitud sirven para reconocer después un reintento del mismo ingreso.
     */
    public static IngresoLibre registrar(UsuarioAutenticado usuario, Categoria categoria, BigDecimal monto,
                                         String descripcion, String comprobante, UUID claveIdempotencia,
                                         String hashSolicitud, Instant ahora) {
        Objects.requireNonNull(usuario, "El usuario es obligatorio");
        Objects.requireNonNull(ahora, "La fecha es obligatoria");
        if ((claveIdempotencia == null) != (hashSolicitud == null)) {
            throw new IllegalArgumentException("La clave de idempotencia y la huella de la solicitud van juntas");
        }

        if (!usuario.tienePermiso(Permiso.REGISTRAR_INGRESO_LIBRE)) {
            throw new SinPermisoException("Solo el administrador puede registrar ingresos libres");
        }
        if (categoria == null || !categoria.esIngresoActiva()) {
            throw new ReglaNegocioException("La categoría debe ser de ingreso y estar activa");
        }
        validarMonto(monto);
        String descripcionLimpia = descripcion == null ? "" : descripcion.strip();
        if (descripcionLimpia.isEmpty() || descripcionLimpia.length() > MAX_DESCRIPCION) {
            throw new ReglaNegocioException("La descripción es obligatoria y tiene máximo " + MAX_DESCRIPCION + " caracteres");
        }
        String comprobanteLimpio = (comprobante == null || comprobante.isBlank()) ? null : comprobante.strip();
        if (comprobanteLimpio != null && comprobanteLimpio.length() > MAX_COMPROBANTE) {
            throw new ReglaNegocioException("El comprobante tiene máximo " + MAX_COMPROBANTE + " caracteres");
        }

        return new IngresoLibre(UUID.randomUUID(), usuario.idCafeteria(), categoria, monto, descripcionLimpia,
                comprobanteLimpio, usuario.id(), ahora, claveIdempotencia, hashSolicitud, false, null);
    }

    /** Vuelve a armar un ingreso libre guardado (lo usa el repositorio al leer la BD). */
    public static IngresoLibre reconstruir(UUID id, UUID idCafeteria, Categoria categoria, BigDecimal monto,
                                           String descripcion, String comprobante, UUID registradoPor,
                                           Instant fechaHora, UUID claveIdempotencia, String hashSolicitud,
                                           boolean anulado, String motivoAnulacion) {
        return new IngresoLibre(id, idCafeteria, categoria, monto, descripcion, comprobante, registradoPor,
                fechaHora, claveIdempotencia, hashSolicitud, anulado, motivoAnulacion);
    }

    /**
     * Un reintento con la misma Idempotency-Key debe traer exactamente lo mismo que el ingreso original.
     * Si trae otra cosa, la clave se está reutilizando para otro ingreso y se rechaza.
     */
    public void verificarMismaSolicitud(String hashOtraSolicitud) {
        if (!Objects.equals(hashSolicitud, hashOtraSolicitud)) {
            throw new ReglaNegocioException("Esta Idempotency-Key ya se usó para un ingreso libre con otros datos; "
                    + "para registrar un ingreso nuevo usa una clave nueva");
        }
    }

    /** Mayor que 0, máximo 2 decimales y 10 dígitos enteros. Nunca se redondea: si no cumple, se rechaza. */
    private static void validarMonto(BigDecimal monto) {
        if (monto == null || monto.signum() <= 0) {
            throw new ReglaNegocioException("El monto debe ser mayor que 0");
        }
        if (Math.max(0, monto.stripTrailingZeros().scale()) > DECIMALES_DINERO) {
            throw new ReglaNegocioException("El monto no puede tener más de " + DECIMALES_DINERO + " decimales");
        }
        if (monto.compareTo(LIMITE_MONTO) >= 0) {
            throw new ReglaNegocioException("El monto no puede tener más de " + DIGITOS_ENTEROS + " dígitos enteros");
        }
    }

    public UUID getId() { return id; }
    public UUID getIdCafeteria() { return idCafeteria; }
    public Categoria getCategoria() { return categoria; }
    public BigDecimal getMonto() { return monto; }
    public String getDescripcion() { return descripcion; }
    public String getComprobante() { return comprobante; }
    public UUID getRegistradoPor() { return registradoPor; }
    public Instant getFechaHora() { return fechaHora; }
    public UUID getClaveIdempotencia() { return claveIdempotencia; }
    public String getHashSolicitud() { return hashSolicitud; }
    public boolean isAnulado() { return anulado; }
    public String getMotivoAnulacion() { return motivoAnulacion; }
}
