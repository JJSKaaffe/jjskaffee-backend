package co.edu.uco.jjskafee.ingresos.aplicacion;

import co.edu.uco.jjskafee.compartido.dominio.excepciones.RecursoNoEncontradoException;
import co.edu.uco.jjskafee.ingresos.aplicacion.comandos.RegistrarVentaComando;
import co.edu.uco.jjskafee.ingresos.aplicacion.puertos.salida.CatalogoProductos;
import co.edu.uco.jjskafee.ingresos.aplicacion.puertos.salida.RepositorioVentas;
import co.edu.uco.jjskafee.ingresos.dominio.EstadoVenta;
import co.edu.uco.jjskafee.ingresos.dominio.MensajesIngresos;
import co.edu.uco.jjskafee.ingresos.dominio.Producto;
import co.edu.uco.jjskafee.ingresos.dominio.Venta;
import co.edu.uco.jjskafee.soporte.Dobles;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static co.edu.uco.jjskafee.soporte.Dobles.AHORA;
import static co.edu.uco.jjskafee.soporte.Dobles.CAFE_DEMO;
import static co.edu.uco.jjskafee.soporte.Dobles.CAJERO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrarIngresoTest {

    private static final Producto CAPUCHINO = new Producto(UUID.randomUUID(), "Capuchino", new BigDecimal("7000"), false, true);
    private static final Producto ALMENDRA = new Producto(UUID.randomUUID(), "Leche de almendra", new BigDecimal("1500"), true, true);

    private final CatalogoFalso catalogo = new CatalogoFalso();
    private final VentasEnMemoria ventas = new VentasEnMemoria();
    private final Dobles.UnidadDeTrabajoEnMemoria unidad = new Dobles.UnidadDeTrabajoEnMemoria();

    private RegistrarIngreso casoUso(java.time.Instant ahora) {
        return new RegistrarIngreso(catalogo, ventas, unidad, new Dobles.MensajesPorClave(), Dobles.relojFijo(ahora));
    }

    @Test
    void registraLaVentaConElTotalDelServidorYLaCafeteriaDelToken() {
        RegistrarVentaComando comando = new RegistrarVentaComando(List.of(new RegistrarVentaComando.Linea(
                CAPUCHINO.id(), 1, List.of(new RegistrarVentaComando.Adicion(ALMENDRA.id(), 1)))));

        Venta venta = casoUso(AHORA).registrar(comando, CAJERO);

        assertEquals(0, new BigDecimal("8500").compareTo(venta.total()));
        assertEquals(CAFE_DEMO, venta.getIdCafeteria());
        assertEquals(CAJERO.id(), venta.getIdCajero());
        assertEquals(AHORA, venta.getFechaHora());
        assertTrue(ventas.guardadas.containsKey(venta.getId()));
        assertEquals(List.of(CAFE_DEMO), unidad.cafeterias);
    }

    @Test
    void lasAdicionesRepetidasSeSuman() {
        RegistrarVentaComando comando = new RegistrarVentaComando(List.of(new RegistrarVentaComando.Linea(
                CAPUCHINO.id(), 1, List.of(new RegistrarVentaComando.Adicion(ALMENDRA.id(), 1),
                new RegistrarVentaComando.Adicion(ALMENDRA.id(), 2)))));

        Venta venta = casoUso(AHORA).registrar(comando, CAJERO);

        assertEquals(2, venta.getDetalles().size());
        assertEquals(3, venta.getDetalles().get(1).getCantidad());
    }

    @Test
    void unProductoQueNoExisteResponde404() {
        RegistrarVentaComando comando = new RegistrarVentaComando(List.of(
                new RegistrarVentaComando.Linea(UUID.randomUUID(), 1, List.of())));
        RecursoNoEncontradoException error = assertThrows(RecursoNoEncontradoException.class,
                () -> casoUso(AHORA).registrar(comando, CAJERO));
        assertEquals(MensajesIngresos.PRODUCTO_NO_EXISTE, error.getCodigo());
    }

    @Test
    void deshacerGuardaLaAnulacionConElMotivoDeLosMensajes() {
        Venta venta = casoUso(AHORA).registrar(new RegistrarVentaComando(List.of(
                new RegistrarVentaComando.Linea(CAPUCHINO.id(), 1, List.of()))), CAJERO);

        Venta anulada = casoUso(AHORA.plusSeconds(30)).deshacer(venta.getId(), CAJERO);

        assertEquals(EstadoVenta.ANULADA, anulada.getEstado());
        assertEquals(MensajesIngresos.MOTIVO_DESHACER.clave(), anulada.getMotivoAnulacion());
        assertTrue(ventas.anuladas.contains(venta.getId()));
    }

    @Test
    void deshacerUnaVentaInexistenteResponde404() {
        RecursoNoEncontradoException error = assertThrows(RecursoNoEncontradoException.class,
                () -> casoUso(AHORA).deshacer(UUID.randomUUID(), CAJERO));
        assertEquals(MensajesIngresos.VENTA_NO_EXISTE, error.getCodigo());
    }

    // ---------------- Dobles ----------------

    private static final class CatalogoFalso implements CatalogoProductos {
        @Override
        public Optional<Producto> buscar(UUID idCafeteria, UUID idProducto) {
            return Optional.ofNullable(Map.of(CAPUCHINO.id(), CAPUCHINO, ALMENDRA.id(), ALMENDRA).get(idProducto));
        }

        @Override
        public Set<UUID> adicionesPermitidas(UUID idCafeteria, UUID idProductoBase) {
            return idProductoBase.equals(CAPUCHINO.id()) ? Set.of(ALMENDRA.id()) : Set.of();
        }
    }

    private static final class VentasEnMemoria implements RepositorioVentas {
        final Map<UUID, Venta> guardadas = new HashMap<>();
        final java.util.List<UUID> anuladas = new java.util.ArrayList<>();

        @Override
        public void guardar(Venta venta) {
            guardadas.put(venta.getId(), venta);
        }

        @Override
        public Optional<Venta> buscarParaAnular(UUID idCafeteria, UUID idVenta) {
            return Optional.ofNullable(guardadas.get(idVenta)).map(v -> Venta.reconstruirEncabezado(
                    v.getId(), v.getIdCafeteria(), v.getIdCajero(), v.getFechaHora(), v.getEstado(),
                    v.getMotivoAnulacion(), v.getAnuladaPor(), v.getAnuladaEn()));
        }

        @Override
        public void registrarAnulacion(Venta venta) {
            anuladas.add(venta.getId());
        }
    }
}
