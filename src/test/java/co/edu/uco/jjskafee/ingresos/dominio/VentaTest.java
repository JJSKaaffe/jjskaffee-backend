package co.edu.uco.jjskafee.ingresos.dominio;

import co.edu.uco.jjskafee.compartido.dominio.excepciones.ExcepcionAplicacion;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.ReglaNegocioException;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.SinPermisoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static co.edu.uco.jjskafee.soporte.Dobles.ADMIN;
import static co.edu.uco.jjskafee.soporte.Dobles.AHORA;
import static co.edu.uco.jjskafee.soporte.Dobles.CAJERO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VentaTest {

    private static final Producto LATTE = new Producto(UUID.randomUUID(), "Café latte", new BigDecimal("6500"), false, true);
    private static final Producto ALMENDRA = new Producto(UUID.randomUUID(), "Leche de almendra", new BigDecimal("1500"), true, true);
    private static final Producto AGOTADO = new Producto(UUID.randomUUID(), "Croissant", new BigDecimal("4500"), false, false);

    @Test
    void elTotalSeCalculaConLosPreciosDelCatalogo() {
        Venta venta = Venta.crear(CAJERO, AHORA);
        venta.agregar(LATTE, 2, Map.of(ALMENDRA, 1), Set.of(ALMENDRA.id()));

        // 2×6500 + 1×1500 = 14 500 (igual que la venta 1 de los datos de prueba)
        assertEquals(0, new BigDecimal("14500").compareTo(venta.total()));
        assertEquals(2, venta.getDetalles().size());
        assertEquals(venta.getDetalles().get(0), venta.getDetalles().get(1).getDetalleBase());
    }

    @Test
    void unaAdicionNoPuedeVenderseSola() {
        Venta venta = Venta.crear(CAJERO, AHORA);
        assertCodigo(MensajesIngresos.PRODUCTO_ES_ADICION,
                assertThrows(ReglaNegocioException.class, () -> venta.agregar(ALMENDRA, 1, Map.of(), Set.of())));
    }

    @Test
    void noSeAgregaUnaAdicionQueElBaseNoPermite() {
        Venta venta = Venta.crear(CAJERO, AHORA);
        assertCodigo(MensajesIngresos.ADICION_NO_PERMITIDA,
                assertThrows(ReglaNegocioException.class, () -> venta.agregar(LATTE, 1, Map.of(ALMENDRA, 1), Set.of())));
    }

    @Test
    void noSeVendeUnProductoNoDisponible() {
        Venta venta = Venta.crear(CAJERO, AHORA);
        assertCodigo(MensajesIngresos.PRODUCTO_NO_DISPONIBLE,
                assertThrows(ReglaNegocioException.class, () -> venta.agregar(AGOTADO, 1, Map.of(), Set.of())));
    }

    @Test
    void laCantidadMinimaEsUno() {
        Venta venta = Venta.crear(CAJERO, AHORA);
        assertCodigo(MensajesIngresos.CANTIDAD_MINIMA,
                assertThrows(ReglaNegocioException.class, () -> venta.agregar(LATTE, 0, Map.of(), Set.of())));
    }

    @Test
    void unaVentaVaciaNoSeRegistra() {
        Venta venta = Venta.crear(CAJERO, AHORA);
        assertCodigo(MensajesIngresos.VENTA_SIN_PRODUCTOS,
                assertThrows(ReglaNegocioException.class, venta::validarParaRegistrar));
    }

    @Test
    void quienLaRegistroPuedeDeshacerlaDentroDe60Segundos() {
        Venta venta = Venta.crear(CAJERO, AHORA);
        venta.agregar(LATTE, 1, Map.of(), Set.of());

        venta.deshacer(CAJERO, AHORA.plusSeconds(60), "motivo");

        assertEquals(EstadoVenta.ANULADA, venta.getEstado());
        assertEquals(CAJERO.id(), venta.getAnuladaPor());
        assertEquals(AHORA.plusSeconds(60), venta.getAnuladaEn());
        assertEquals("motivo", venta.getMotivoAnulacion());
    }

    @Test
    void despuesDe60SegundosNoSePuedeDeshacer() {
        Venta venta = Venta.crear(CAJERO, AHORA);
        assertCodigo(MensajesIngresos.TIEMPO_DESHACER_VENCIDO, assertThrows(ReglaNegocioException.class,
                () -> venta.deshacer(CAJERO, AHORA.plusSeconds(61), "motivo")));
    }

    @Test
    void otroUsuarioNoPuedeDeshacerla() {
        Venta venta = Venta.crear(CAJERO, AHORA);
        assertCodigo(MensajesIngresos.SOLO_QUIEN_REGISTRO_DESHACE, assertThrows(SinPermisoException.class,
                () -> venta.deshacer(ADMIN, AHORA.plusSeconds(5), "motivo")));
    }

    @Test
    void noSeDeshaceDosVeces() {
        Venta venta = Venta.crear(CAJERO, AHORA);
        venta.deshacer(CAJERO, AHORA.plusSeconds(5), "motivo");
        assertCodigo(MensajesIngresos.VENTA_YA_ANULADA, assertThrows(ReglaNegocioException.class,
                () -> venta.deshacer(CAJERO, AHORA.plusSeconds(10), "motivo")));
    }

    private static void assertCodigo(MensajesIngresos esperado, ExcepcionAplicacion error) {
        assertEquals(esperado, error.getCodigo());
    }
}
