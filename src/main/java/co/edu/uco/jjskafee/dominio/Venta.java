package co.edu.uco.jjskafee.dominio;

import co.edu.uco.jjskafee.dominio.enums.EstadoVenta;
import co.edu.uco.jjskafee.dominio.excepciones.ReglaNegocioException;
import co.edu.uco.jjskafee.dominio.excepciones.SinPermisoException;

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
 * Una venta = un ingreso de la cafetería (RF24–RF27).
 * El total SIEMPRE se calcula aquí con los precios del catálogo;
 * nunca se acepta un total enviado por el navegador.
 */
public class Venta {

    /** Tiempo máximo para deshacer una venta (ADR-0008). */
    public static final Duration TIEMPO_PARA_DESHACER = Duration.ofSeconds(60);

    private final UUID id;
    private final UUID idCafeteria;
    private final UUID idCajero;
    private final Instant fechaHora;
    private final List<DetalleVenta> detalles;
    private EstadoVenta estado;
    private String motivoAnulacion;

    private Venta(UUID id, UUID idCafeteria, UUID idCajero, Instant fechaHora, List<DetalleVenta> detalles,
                  EstadoVenta estado, String motivoAnulacion) {
        this.id = id;
        this.idCafeteria = idCafeteria;
        this.idCajero = idCajero;
        this.fechaHora = fechaHora;
        this.detalles = detalles;
        this.estado = estado;
        this.motivoAnulacion = motivoAnulacion;
    }

    /** Empieza una venta vacía. Pueden vender el cajero y el administrador. */
    public static Venta crear(UsuarioAutenticado cajero, Instant ahora) {
        Objects.requireNonNull(cajero, "El usuario es obligatorio");
        Objects.requireNonNull(ahora, "La fecha es obligatoria");
        return new Venta(UUID.randomUUID(), cajero.idCafeteria(), cajero.id(), ahora,
                new ArrayList<>(), EstadoVenta.CONFIRMADA, null);
    }

    /** Vuelve a armar una venta guardada (lo usa el repositorio al leer la BD). */
    public static Venta reconstruir(UUID id, UUID idCafeteria, UUID idCajero, Instant fechaHora,
                                    List<DetalleVenta> detalles, EstadoVenta estado, String motivoAnulacion) {
        return new Venta(id, idCafeteria, idCajero, fechaHora, new ArrayList<>(detalles), estado, motivoAnulacion);
    }

    /**
     * Agrega un producto base con sus adiciones.
     *
     * @param base                 producto base (café, pan…)
     * @param cantidad             unidades del producto base (mínimo 1)
     * @param adiciones            adición → cantidad (puede estar vacío)
     * @param adicionesPermitidas  ids de las adiciones que se le pueden poner a ese base (tabla producto_adicion)
     */
    public void agregar(Producto base, int cantidad, Map<Producto, Integer> adiciones, Set<UUID> adicionesPermitidas) {
        Objects.requireNonNull(base, "El producto es obligatorio");
        if (!base.esBase()) {
            throw new ReglaNegocioException(base.getNombre() + " es una adición; debe ir junto a un producto base");
        }
        if (!base.isDisponible()) {
            throw new ReglaNegocioException(base.getNombre() + " no está disponible");
        }
        if (cantidad < 1) {
            throw new ReglaNegocioException("La cantidad debe ser mínimo 1");
        }

        DetalleVenta detalleBase = new DetalleVenta(UUID.randomUUID(), base, cantidad, base.getPrecio(), null);
        detalles.add(detalleBase);

        Map<Producto, Integer> lista = adiciones == null ? Map.of() : adiciones;
        Set<UUID> permitidas = adicionesPermitidas == null ? Set.of() : adicionesPermitidas;
        lista.forEach((adicion, cantidadAdicion) -> {
            if (!adicion.esAdicion() || !permitidas.contains(adicion.getId())) {
                throw new ReglaNegocioException(adicion.getNombre() + " no se puede agregar a " + base.getNombre());
            }
            if (!adicion.isDisponible()) {
                throw new ReglaNegocioException(adicion.getNombre() + " no está disponible");
            }
            if (cantidadAdicion == null || cantidadAdicion < 1) {
                throw new ReglaNegocioException("La cantidad de " + adicion.getNombre() + " debe ser mínimo 1");
            }
            detalles.add(new DetalleVenta(UUID.randomUUID(), adicion, cantidadAdicion, adicion.getPrecio(), detalleBase));
        });
    }

    /** Total = suma de los productos base + sus adiciones. */
    public BigDecimal total() {
        return detalles.stream().map(DetalleVenta::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Se llama antes de guardar: una venta sin productos no se registra. */
    public void validarParaRegistrar() {
        if (detalles.isEmpty()) {
            throw new ReglaNegocioException("La venta debe tener al menos un producto");
        }
    }

    /** Deshacer: solo quien la registró y dentro de los 60 segundos (ADR-0008). */
    public void deshacer(UsuarioAutenticado usuario, Instant ahora) {
        if (!usuario.id().equals(idCajero)) {
            throw new SinPermisoException("Solo quien registró la venta puede deshacerla");
        }
        if (estado == EstadoVenta.ANULADA) {
            throw new ReglaNegocioException("La venta ya estaba anulada");
        }
        if (Duration.between(fechaHora, ahora).compareTo(TIEMPO_PARA_DESHACER) > 0) {
            throw new ReglaNegocioException("Ya pasó el tiempo para deshacer; pide al administrador que la anule");
        }
        estado = EstadoVenta.ANULADA;
        motivoAnulacion = "Deshecha por el cajero";
    }

    public UUID getId() { return id; }
    public UUID getIdCafeteria() { return idCafeteria; }
    public UUID getIdCajero() { return idCajero; }
    public Instant getFechaHora() { return fechaHora; }
    public List<DetalleVenta> getDetalles() { return Collections.unmodifiableList(detalles); }
    public EstadoVenta getEstado() { return estado; }
    public String getMotivoAnulacion() { return motivoAnulacion; }
}
