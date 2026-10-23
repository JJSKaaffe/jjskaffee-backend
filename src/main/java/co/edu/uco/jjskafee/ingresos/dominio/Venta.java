package co.edu.uco.jjskafee.ingresos.dominio;

import co.edu.uco.jjskafee.compartido.dominio.Producto;
import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.ReglaNegocioException;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.SinPermisoException;
import co.edu.uco.jjskafee.ingresos.dominio.enums.EstadoVenta;

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
 * Una venta = un ingreso de la cafetería (RF24–RF27). En esta versión todo ingreso es una venta.
 * El total SIEMPRE se calcula aquí con los precios del catálogo;
 * nunca se acepta un total enviado por el navegador.
 */
public class Venta {

    /** Tiempo máximo para deshacer una venta (ADR-0008). */
    public static final Duration TIEMPO_PARA_DESHACER = Duration.ofSeconds(60);
    /** Máximo de líneas por venta: cada producto base y cada adición distinta es una línea. */
    public static final int MAXIMO_LINEAS = 50;
    public static final int CANTIDAD_MINIMA = 1;
    public static final int CANTIDAD_MAXIMA = 99;
    /** Pesos con centavos: igual que numeric(12,2) en la BD. */
    public static final int DECIMALES_DINERO = 2;

    private final UUID id;
    private final UUID idCafeteria;
    private final UUID idCajero;
    private final Instant fechaHora;
    private final List<DetalleVenta> detalles;
    private final UUID claveIdempotencia;   // null si el frontend no mandó Idempotency-Key
    private final String hashSolicitud;     // huella de lo pedido; null si no hay clave
    private EstadoVenta estado;
    private String motivoAnulacion;

    private Venta(UUID id, UUID idCafeteria, UUID idCajero, Instant fechaHora, List<DetalleVenta> detalles,
                  UUID claveIdempotencia, String hashSolicitud, EstadoVenta estado, String motivoAnulacion) {
        this.id = id;
        this.idCafeteria = idCafeteria;
        this.idCajero = idCajero;
        this.fechaHora = fechaHora;
        this.detalles = detalles;
        this.claveIdempotencia = claveIdempotencia;
        this.hashSolicitud = hashSolicitud;
        this.estado = estado;
        this.motivoAnulacion = motivoAnulacion;
    }

    /** Empieza una venta vacía sin clave de idempotencia. Pueden vender el cajero y el administrador. */
    public static Venta crear(UsuarioAutenticado cajero, Instant ahora) {
        return crear(cajero, null, null, ahora);
    }

    /**
     * Empieza una venta vacía con la clave Idempotency-Key y la huella de la solicitud,
     * para reconocer después un reintento de la misma venta.
     */
    public static Venta crear(UsuarioAutenticado cajero, UUID claveIdempotencia, String hashSolicitud, Instant ahora) {
        Objects.requireNonNull(cajero, "El usuario es obligatorio");
        Objects.requireNonNull(ahora, "La fecha es obligatoria");
        if ((claveIdempotencia == null) != (hashSolicitud == null)) {
            throw new IllegalArgumentException("La clave de idempotencia y la huella de la solicitud van juntas");
        }
        return new Venta(UUID.randomUUID(), cajero.idCafeteria(), cajero.id(), ahora,
                new ArrayList<>(), claveIdempotencia, hashSolicitud, EstadoVenta.CONFIRMADA, null);
    }

    /** Vuelve a armar una venta guardada (lo usa el repositorio al leer la BD). */
    public static Venta reconstruir(UUID id, UUID idCafeteria, UUID idCajero, Instant fechaHora,
                                    List<DetalleVenta> detalles, UUID claveIdempotencia, String hashSolicitud,
                                    EstadoVenta estado, String motivoAnulacion) {
        return new Venta(id, idCafeteria, idCajero, fechaHora, new ArrayList<>(detalles),
                claveIdempotencia, hashSolicitud, estado, motivoAnulacion);
    }

    /**
     * Agrega un producto base con sus adiciones.
     *
     * @param base                 producto base (café, pan…)
     * @param cantidad             unidades del producto base (1 a 99)
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
        validarPrecio(base);
        validarCantidad(cantidad, "La cantidad de " + base.getNombre());

        Map<Producto, Integer> lista = adiciones == null ? Map.of() : adiciones;
        if (detalles.size() + 1 + lista.size() > MAXIMO_LINEAS) {
            throw new ReglaNegocioException("La venta no puede tener más de " + MAXIMO_LINEAS
                    + " líneas (productos y adiciones)");
        }

        DetalleVenta detalleBase = new DetalleVenta(UUID.randomUUID(), base, cantidad, base.getPrecio(), null);
        List<DetalleVenta> nuevos = new ArrayList<>();
        nuevos.add(detalleBase);

        Set<UUID> permitidas = adicionesPermitidas == null ? Set.of() : adicionesPermitidas;
        lista.forEach((adicion, cantidadAdicion) -> {
            if (!adicion.esAdicion() || !permitidas.contains(adicion.getId())) {
                throw new ReglaNegocioException(adicion.getNombre() + " no se puede agregar a " + base.getNombre());
            }
            if (!adicion.isDisponible()) {
                throw new ReglaNegocioException(adicion.getNombre() + " no está disponible");
            }
            validarPrecio(adicion);
            if (cantidadAdicion == null) {
                throw new ReglaNegocioException("La cantidad de " + adicion.getNombre() + " es obligatoria");
            }
            validarCantidad(cantidadAdicion, "La cantidad de " + adicion.getNombre());
            nuevos.add(new DetalleVenta(UUID.randomUUID(), adicion, cantidadAdicion, adicion.getPrecio(), detalleBase));
        });
        // Solo se agregan si el base y todas sus adiciones son válidos
        detalles.addAll(nuevos);
    }

    /** Total = suma exacta (BigDecimal, sin redondear) de los productos base + sus adiciones. */
    public BigDecimal total() {
        return detalles.stream().map(DetalleVenta::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Se llama antes de guardar: una venta sin productos o con un total inválido no se registra. */
    public void validarParaRegistrar() {
        if (detalles.isEmpty()) {
            throw new ReglaNegocioException("La venta debe tener al menos un producto");
        }
        BigDecimal total = total();
        if (total.signum() <= 0) {
            throw new ReglaNegocioException("El total de la venta debe ser mayor que 0");
        }
        if (decimalesDe(total) > DECIMALES_DINERO) {
            throw new ReglaNegocioException("El total de la venta no puede tener más de " + DECIMALES_DINERO + " decimales");
        }
    }

    /**
     * Un reintento con la misma Idempotency-Key debe traer exactamente lo mismo que la venta original.
     * Si trae otra cosa, la clave se está reutilizando para otra venta y se rechaza.
     */
    public void verificarMismaSolicitud(String hashOtraSolicitud) {
        if (!Objects.equals(hashSolicitud, hashOtraSolicitud)) {
            throw new ReglaNegocioException("Esta Idempotency-Key ya se usó para una venta con otros productos o "
                    + "cantidades; para registrar una venta nueva usa una clave nueva");
        }
    }

    private static void validarPrecio(Producto producto) {
        if (producto.getPrecio().signum() <= 0) {
            throw new ReglaNegocioException(producto.getNombre() + " no tiene un precio mayor que 0");
        }
        if (decimalesDe(producto.getPrecio()) > DECIMALES_DINERO) {
            throw new ReglaNegocioException("El precio de " + producto.getNombre() + " no puede tener más de "
                    + DECIMALES_DINERO + " decimales");
        }
    }

    private static void validarCantidad(int cantidad, String queCantidad) {
        if (cantidad < CANTIDAD_MINIMA || cantidad > CANTIDAD_MAXIMA) {
            throw new ReglaNegocioException(queCantidad + " debe estar entre " + CANTIDAD_MINIMA
                    + " y " + CANTIDAD_MAXIMA);
        }
    }

    /** Decimales que de verdad tiene el valor: 6500.00 tiene 0, 0.30 tiene 1, 1500.005 tiene 3. */
    private static int decimalesDe(BigDecimal valor) {
        return Math.max(0, valor.stripTrailingZeros().scale());
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
    public UUID getClaveIdempotencia() { return claveIdempotencia; }
    public String getHashSolicitud() { return hashSolicitud; }
    public EstadoVenta getEstado() { return estado; }
    public String getMotivoAnulacion() { return motivoAnulacion; }
}
