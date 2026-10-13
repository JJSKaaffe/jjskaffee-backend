package co.edu.uco.jjskafee.servicios.casouso;

import co.edu.uco.jjskafee.dominio.Producto;
import co.edu.uco.jjskafee.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.dominio.Venta;
import co.edu.uco.jjskafee.dominio.excepciones.RecursoNoEncontradoException;
import co.edu.uco.jjskafee.servicios.input.dto.request.AdicionRequest;
import co.edu.uco.jjskafee.servicios.input.dto.request.IngresoRequest;
import co.edu.uco.jjskafee.servicios.input.dto.request.ItemRequest;
import co.edu.uco.jjskafee.servicios.input.dto.response.IngresoResponse;
import co.edu.uco.jjskafee.servicios.input.ports.RegistrarIngresoUseCase;
import co.edu.uco.jjskafee.servicios.output.ports.CatalogoRepository;
import co.edu.uco.jjskafee.servicios.output.ports.UnidadDeTrabajo;
import co.edu.uco.jjskafee.servicios.output.ports.VentaRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso: registrar una venta con productos y adiciones (RF24–RF27)
 * y deshacerla dentro de los 60 segundos (ADR-0008).
 */
public class RegistrarIngreso implements RegistrarIngresoUseCase {

    private final CatalogoRepository catalogo;
    private final VentaRepository ventas;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final Clock reloj;

    public RegistrarIngreso(CatalogoRepository catalogo, VentaRepository ventas,
                            UnidadDeTrabajo unidadDeTrabajo, Clock reloj) {
        this.catalogo = Objects.requireNonNull(catalogo);
        this.ventas = Objects.requireNonNull(ventas);
        this.unidadDeTrabajo = Objects.requireNonNull(unidadDeTrabajo);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public IngresoResponse registrar(IngresoRequest solicitud, UsuarioAutenticado usuario) {
        // Todo dentro de una sola transacción: venta y detalles se guardan juntos o no se guarda nada (ADR-0002)
        return unidadDeTrabajo.ejecutar(usuario.idCafeteria(), () -> {
            Venta venta = Venta.crear(usuario, Instant.now(reloj));

            for (ItemRequest item : solicitud.items()) {
                Producto base = buscarProducto(item.idProducto());
                venta.agregar(base, item.cantidad(), adicionesDe(item), catalogo.adicionesPermitidas(base.getId()));
            }

            venta.validarParaRegistrar();
            ventas.guardar(venta);
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
