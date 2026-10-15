package co.edu.uco.jjskafee.ingresos.dominio;

import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.ReglaNegocioException;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.SinPermisoException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Una venta = un ingreso de la cafetería (RF24–RF27). Tablas venta + detalle_venta.
 * El total SIEMPRE se calcula aquí con los precios del catálogo; nunca se acepta
 * un total enviado por el navegador.
 */
public final class Venta {

    /** Tiempo máximo para deshacer una venta (ADR-0008). */
    public static final Duration TIEMPO_PARA_DESHACER = Duration.ofSeconds(60);

    private final UUID id;
    private final UUID idCafeteria;
    private final UUID idCajero;
    private final Instant fechaHora;
    private final List<DetalleVenta> detalles;
    private EstadoVenta estado;
    private String motivoAnulacion;
    private UUID anuladaPor;
    private Instant anuladaEn;

    private Venta(UUID id, UUID idCafeteria, UUID idCajero, Instant fechaHora, List<DetalleVenta> detalles,
                  EstadoVenta estado, String motivoAnulacion, UUID anuladaPor, Instant anuladaEn) {
        this.id = Objects.requireNonNull(id);
        this.idCafeteria = Objects.requireNonNull(idCafeteria);
        this.idCajero = Objects.requireNonNull(idCajero);
        this.fechaHora = Objects.requireNonNull(fechaHora);
        this.detalles = detalles;
        this.estado = Objects.requireNonNull(estado);
        this.motivoAnulacion = motivoAnulacion;
        this.anuladaPor = anuladaPor;
        this.anuladaEn = anuladaEn;
    }

    /** Empieza una venta vacía. Pueden vender el cajero y el administrador. */
    public static Venta crear(UsuarioAutenticado vendedor, Instant ahora) {
        Objects.requireNonNull(vendedor);
        return new Venta(UUID.randomUUID(), vendedor.idCafeteria(), vendedor.id(), ahora,
                new ArrayList<>(), EstadoVenta.CONFIRMADA, null, null, null);
    }

    /**
     * Vuelve a armar el encabezado de una venta guardada para anularla.
     * Los detalles no hacen falta para anular, por eso la lista queda vacía.
     */
    public static Venta reconstruirEncabezado(UUID id, UUID idCafeteria, UUID idCajero, Instant fechaHora,
                                              EstadoVenta estado, String motivoAnulacion, UUID anuladaPor,
                                              Instant anuladaEn) {
        return new Venta(id, idCafeteria, idCajero, fechaHora, new ArrayList<>(), estado, motivoAnulacion,
                anuladaPor, anuladaEn);
    }

    /**
     * Agrega un producto base con sus adiciones.
     *
     * @param base                producto base (café, pan…)
     * @param cantidad            unidades del producto base (mínimo 1)
     * @param adiciones           adición → cantidad (puede estar vacío)
     * @param adicionesPermitidas ids de adiciones que admite ese base (tabla producto_adicion)
     */
    public void agregar(Producto base, int cantidad, Map<Producto, Integer> adiciones, Set<UUID> adicionesPermitidas) {
        Objects.requireNonNull(base);
        if (!base.esBase()) {
            throw new ReglaNegocioException(MensajesIngresos.PRODUCTO_ES_ADICION, base.nombre());
        }
        if (!base.disponible()) {
            throw new ReglaNegocioException(MensajesIngresos.PRODUCTO_NO_DISPONIBLE, base.nombre());
        }
        if (cantidad < 1) {
            throw new ReglaNegocioException(MensajesIngresos.CANTIDAD_MINIMA);
        }

        DetalleVenta lineaBase = new DetalleVenta(UUID.randomUUID(), base, cantidad, base.precio(), null);
        detalles.add(lineaBase);

        Map<Producto, Integer> lista = adiciones == null ? Map.of() : adiciones;
        Set<UUID> permitidas = adicionesPermitidas == null ? Set.of() : adicionesPermitidas;
        lista.forEach((adicion, cantidadAdicion) -> {
            if (!adicion.esAdicion() || !permitidas.contains(adicion.id())) {
                throw new ReglaNegocioException(MensajesIngresos.ADICION_NO_PERMITIDA, adicion.nombre(), base.nombre());
            }
            if (!adicion.disponible()) {
                throw new ReglaNegocioException(MensajesIngresos.PRODUCTO_NO_DISPONIBLE, adicion.nombre());
            }
            if (cantidadAdicion == null || cantidadAdicion < 1) {
                throw new ReglaNegocioException(MensajesIngresos.CANTIDAD_ADICION_MINIMA, adicion.nombre());
            }
            detalles.add(new DetalleVenta(UUID.randomUUID(), adicion, cantidadAdicion, adicion.precio(), lineaBase));
        });
    }

    /** Total = suma de los productos base + sus adiciones. */
    public BigDecimal total() {
        return detalles.stream().map(DetalleVenta::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Se llama antes de guardar: una venta sin productos no se registra. */
    public void validarParaRegistrar() {
        if (detalles.isEmpty()) {
            throw new ReglaNegocioException(MensajesIngresos.VENTA_SIN_PRODUCTOS);
        }
    }

    /**
     * Deshacer: solo quien la registró y dentro de los 60 segundos (ADR-0008).
     * La BD exige motivo, autorizador y fecha de anulación (CHECK de la tabla venta).
     *
     * @param motivo texto del motivo; lo trae el caso de uso desde i18n/mensajes.properties
     */
    public void deshacer(UsuarioAutenticado usuario, Instant ahora, String motivo) {
        if (!usuario.id().equals(idCajero)) {
            throw new SinPermisoException(MensajesIngresos.SOLO_QUIEN_REGISTRO_DESHACE);
        }
        if (estado == EstadoVenta.ANULADA) {
            throw new ReglaNegocioException(MensajesIngresos.VENTA_YA_ANULADA);
        }
        if (Duration.between(fechaHora, ahora).compareTo(TIEMPO_PARA_DESHACER) > 0) {
            throw new ReglaNegocioException(MensajesIngresos.TIEMPO_DESHACER_VENCIDO, TIEMPO_PARA_DESHACER.toSeconds());
        }
        estado = EstadoVenta.ANULADA;
        motivoAnulacion = Objects.requireNonNull(motivo);
        anuladaPor = usuario.id();
        anuladaEn = ahora;
    }

    public UUID getId() { return id; }
    public UUID getIdCafeteria() { return idCafeteria; }
    public UUID getIdCajero() { return idCajero; }
    public Instant getFechaHora() { return fechaHora; }
    public List<DetalleVenta> getDetalles() { return Collections.unmodifiableList(detalles); }
    public EstadoVenta getEstado() { return estado; }
    public String getMotivoAnulacion() { return motivoAnulacion; }
    public UUID getAnuladaPor() { return anuladaPor; }
    public Instant getAnuladaEn() { return anuladaEn; }
}
