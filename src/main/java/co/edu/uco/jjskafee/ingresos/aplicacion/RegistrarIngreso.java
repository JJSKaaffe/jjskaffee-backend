package co.edu.uco.jjskafee.ingresos.aplicacion;

import co.edu.uco.jjskafee.compartido.aplicacion.puertos.ProveedorMensajes;
import co.edu.uco.jjskafee.compartido.aplicacion.puertos.UnidadDeTrabajo;
import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.RecursoNoEncontradoException;
import co.edu.uco.jjskafee.ingresos.aplicacion.comandos.RegistrarVentaComando;
import co.edu.uco.jjskafee.ingresos.aplicacion.puertos.entrada.RegistrarIngresoCasoUso;
import co.edu.uco.jjskafee.ingresos.aplicacion.puertos.salida.CatalogoProductos;
import co.edu.uco.jjskafee.ingresos.aplicacion.puertos.salida.RepositorioVentas;
import co.edu.uco.jjskafee.ingresos.dominio.MensajesIngresos;
import co.edu.uco.jjskafee.ingresos.dominio.Producto;
import co.edu.uco.jjskafee.ingresos.dominio.Venta;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso: registrar una venta con productos y adiciones (RF24–RF27)
 * y deshacerla dentro de los 60 segundos (ADR-0008).
 * Java puro: lo crea IngresosConfiguracion.
 */
public class RegistrarIngreso implements RegistrarIngresoCasoUso {

    private final CatalogoProductos catalogo;
    private final RepositorioVentas ventas;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final ProveedorMensajes mensajes;
    private final Clock reloj;

    public RegistrarIngreso(CatalogoProductos catalogo, RepositorioVentas ventas, UnidadDeTrabajo unidadDeTrabajo,
                            ProveedorMensajes mensajes, Clock reloj) {
        this.catalogo = Objects.requireNonNull(catalogo);
        this.ventas = Objects.requireNonNull(ventas);
        this.unidadDeTrabajo = Objects.requireNonNull(unidadDeTrabajo);
        this.mensajes = Objects.requireNonNull(mensajes);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public Venta registrar(RegistrarVentaComando comando, UsuarioAutenticado usuario) {
        UUID idCafeteria = usuario.idCafeteria();
        // Venta y detalles en una sola transacción: se guarda todo o nada (ADR-0002)
        return unidadDeTrabajo.ejecutar(idCafeteria, () -> {
            Venta venta = Venta.crear(usuario, Instant.now(reloj));
            for (RegistrarVentaComando.Linea linea : comando.lineas()) {
                Producto base = buscarProducto(idCafeteria, linea.idProducto());
                venta.agregar(base, linea.cantidad(), adicionesDe(idCafeteria, linea),
                        catalogo.adicionesPermitidas(idCafeteria, base.id()));
            }
            venta.validarParaRegistrar();
            ventas.guardar(venta);
            return venta;
        });
    }

    @Override
    public Venta deshacer(UUID idVenta, UsuarioAutenticado usuario) {
        UUID idCafeteria = usuario.idCafeteria();
        return unidadDeTrabajo.ejecutar(idCafeteria, () -> {
            Venta venta = ventas.buscarParaAnular(idCafeteria, idVenta)
                    .orElseThrow(() -> new RecursoNoEncontradoException(MensajesIngresos.VENTA_NO_EXISTE));
            venta.deshacer(usuario, Instant.now(reloj), mensajes.obtener(MensajesIngresos.MOTIVO_DESHACER));
            ventas.registrarAnulacion(venta);
            return venta;
        });
    }

    /** Convierte las adiciones de la línea en productos; si una se repite, suma las cantidades. */
    private Map<Producto, Integer> adicionesDe(UUID idCafeteria, RegistrarVentaComando.Linea linea) {
        Map<Producto, Integer> adiciones = new LinkedHashMap<>();
        for (RegistrarVentaComando.Adicion adicion : linea.adiciones()) {
            adiciones.merge(buscarProducto(idCafeteria, adicion.idProducto()), adicion.cantidad(), Integer::sum);
        }
        return adiciones;
    }

    private Producto buscarProducto(UUID idCafeteria, UUID idProducto) {
        return catalogo.buscar(idCafeteria, idProducto)
                .orElseThrow(() -> new RecursoNoEncontradoException(MensajesIngresos.PRODUCTO_NO_EXISTE, idProducto));
    }
}
