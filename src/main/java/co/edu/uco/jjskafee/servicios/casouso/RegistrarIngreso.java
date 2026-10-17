package co.edu.uco.jjskafee.servicios.casouso;

import co.edu.uco.jjskafee.dominio.Producto;
import co.edu.uco.jjskafee.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.dominio.Venta;
import co.edu.uco.jjskafee.dominio.excepciones.RecursoNoEncontradoException;
import co.edu.uco.jjskafee.dominio.excepciones.ReglaNegocioException;
import co.edu.uco.jjskafee.servicios.input.dto.request.AdicionRequest;
import co.edu.uco.jjskafee.servicios.input.dto.request.IngresoRequest;
import co.edu.uco.jjskafee.servicios.input.dto.request.ItemRequest;
import co.edu.uco.jjskafee.servicios.input.dto.response.IngresoResponse;
import co.edu.uco.jjskafee.servicios.input.ports.RegistrarIngresoUseCase;
import co.edu.uco.jjskafee.servicios.output.ports.CafeteriaRepository;
import co.edu.uco.jjskafee.servicios.output.ports.CatalogoRepository;
import co.edu.uco.jjskafee.servicios.output.ports.UnidadDeTrabajo;
import co.edu.uco.jjskafee.servicios.output.ports.VentaRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Caso de uso: registrar una venta con productos y adiciones (RF24–RF27)
 * y deshacerla dentro de los 60 segundos (ADR-0008).
 * En esta versión todo ingreso se registra como una venta de productos del menú.
 */
public class RegistrarIngreso implements RegistrarIngresoUseCase {

    private final CatalogoRepository catalogo;
    private final VentaRepository ventas;
    private final CafeteriaRepository cafeterias;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final Clock reloj;

    public RegistrarIngreso(CatalogoRepository catalogo, VentaRepository ventas, CafeteriaRepository cafeterias,
                            UnidadDeTrabajo unidadDeTrabajo, Clock reloj) {
        this.catalogo = Objects.requireNonNull(catalogo);
        this.ventas = Objects.requireNonNull(ventas);
        this.cafeterias = Objects.requireNonNull(cafeterias);
        this.unidadDeTrabajo = Objects.requireNonNull(unidadDeTrabajo);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public IngresoResponse registrar(IngresoRequest solicitud, UUID claveIdempotencia, UsuarioAutenticado usuario) {
        // Todo dentro de una sola transacción: venta y detalles se guardan juntos o no se guarda nada (ADR-0002)
        return unidadDeTrabajo.ejecutar(usuario.idCafeteria(), () -> {
            if (!cafeterias.estaActiva(usuario.idCafeteria())) {
                throw new ReglaNegocioException("La cafetería no está activa; no se pueden registrar ingresos");
            }

            String hashSolicitud = claveIdempotencia == null ? null : huellaDe(solicitud);
            if (claveIdempotencia != null) {
                Optional<Venta> previa = ventas.buscarPorClaveIdempotencia(claveIdempotencia);
                if (previa.isPresent()) {
                    return repetida(previa.get(), hashSolicitud);
                }
            }

            Venta venta = Venta.crear(usuario, claveIdempotencia, hashSolicitud, Instant.now(reloj));
            for (ItemRequest item : solicitud.items()) {
                Producto base = buscarProducto(item.idProducto());
                venta.agregar(base, item.cantidad(), adicionesDe(item), catalogo.adicionesPermitidas(base.getId()));
            }
            venta.validarParaRegistrar();

            if (!ventas.guardar(venta)) {
                // Otra petición con la misma clave se guardó primero: se responde con esa venta
                Venta ganadora = ventas.buscarPorClaveIdempotencia(claveIdempotencia)
                        .orElseThrow(() -> new IllegalStateException("La venta con la clave repetida no se encontró"));
                return repetida(ganadora, hashSolicitud);
            }
            return IngresoResponse.desde(venta);
        });
    }

    @Override
    public void deshacer(UUID idVenta, UsuarioAutenticado usuario) {
        unidadDeTrabajo.ejecutar(usuario.idCafeteria(), () -> {
            Venta venta = ventas.buscarPorId(idVenta)
                    .orElseThrow(() -> new RecursoNoEncontradoException("La venta no existe"));
            venta.deshacer(usuario, Instant.now(reloj));
            ventas.actualizarEstado(venta);
            return null;
        });
    }

    /** Reintento con la misma Idempotency-Key: solo vale si pide exactamente lo mismo. */
    private static IngresoResponse repetida(Venta previa, String hashSolicitud) {
        previa.verificarMismaSolicitud(hashSolicitud);
        return IngresoResponse.desde(previa);
    }

    /**
     * Huella SHA-256 (hexadecimal) de lo que se pidió: productos, cantidades y adiciones, en orden.
     * Una adición sin cantidad cuenta como 1, igual que al registrar.
     */
    static String huellaDe(IngresoRequest solicitud) {
        StringBuilder texto = new StringBuilder();
        for (ItemRequest item : solicitud.items()) {
            texto.append(item.idProducto()).append('x').append(item.cantidad());
            for (AdicionRequest a : item.adicionesOVacia()) {
                texto.append('+').append(a.idProducto()).append('x').append(a.cantidadOUno());
            }
            texto.append(';');
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);   // toda JVM lo trae
        }
    }

    /** Convierte las adiciones del request en productos; si una se repite, suma las cantidades. */
    private Map<Producto, Integer> adicionesDe(ItemRequest item) {
        Map<Producto, Integer> adiciones = new LinkedHashMap<>();
        for (AdicionRequest a : item.adicionesOVacia()) {
            adiciones.merge(buscarProducto(a.idProducto()), a.cantidadOUno(), Integer::sum);
        }
        return adiciones;
    }

    private Producto buscarProducto(UUID idProducto) {
        return catalogo.buscarProducto(idProducto)
                .orElseThrow(() -> new RecursoNoEncontradoException("El producto " + idProducto + " no existe"));
    }
}
