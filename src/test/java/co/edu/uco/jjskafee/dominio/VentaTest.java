package co.edu.uco.jjskafee.dominio;

import co.edu.uco.jjskafee.dominio.enums.EstadoVenta;
import co.edu.uco.jjskafee.dominio.excepciones.ReglaNegocioException;
import co.edu.uco.jjskafee.dominio.excepciones.SinPermisoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static co.edu.uco.jjskafee.DatosPrueba.*;
import static org.junit.jupiter.api.Assertions.*;

class VentaTest {

    @Test
    void calculaElTotalConProductosYAdiciones() {
        Venta venta = Venta.crear(CAJERO, AHORA);
        venta.agregar(LATTE, 2, Map.of(ALMENDRA, 1), ADICIONES_LATTE);   // 2 x 6500 + 1500
        venta.agregar(PANDEBONO, 1, Map.of(), Set.of());                 // 2500

        assertEquals(new BigDecimal("17000"), venta.total());
        assertEquals(3, venta.getDetalles().size());
        assertEquals(EstadoVenta.CONFIRMADA, venta.getEstado());
    }

    @Test
    void noPermiteAdicionesQueNoCorrespondenAlProducto() {
        Venta venta = Venta.crear(CAJERO, AHORA);
        assertThrows(ReglaNegocioException.class,
                () -> venta.agregar(PANDEBONO, 1, Map.of(ALMENDRA, 1), Set.of()));
    }

    @Test
    void unaAdicionNoSePuedeVenderSola() {
        Venta venta = Venta.crear(CAJERO, AHORA);
        assertThrows(ReglaNegocioException.class, () -> venta.agregar(ALMENDRA, 1, Map.of(), Set.of()));
    }

    @Test
    void unaVentaVaciaNoSeRegistra() {
        Venta venta = Venta.crear(CAJERO, AHORA);
        assertThrows(ReglaNegocioException.class, venta::validarParaRegistrar);
    }

    @Test
    void seDeshaceDentroDeLos60Segundos() {
        Venta venta = Venta.crear(CAJERO, AHORA);
        venta.agregar(PANDEBONO, 1, Map.of(), Set.of());

        venta.deshacer(CAJERO, AHORA.plusSeconds(59));

        assertEquals(EstadoVenta.ANULADA, venta.getEstado());
    }

    @Test
    void noSeDeshaceDespuesDe60Segundos() {
        Venta venta = Venta.crear(CAJERO, AHORA);
        assertThrows(ReglaNegocioException.class, () -> venta.deshacer(CAJERO, AHORA.plusSeconds(61)));
    }

    @Test
    void soloQuienLaRegistroPuedeDeshacerla() {
        Venta venta = Venta.crear(CAJERO, AHORA);
        assertThrows(SinPermisoException.class, () -> venta.deshacer(ADMIN, AHORA.plusSeconds(5)));
    }
}
