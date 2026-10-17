package co.edu.uco.jjskafee.dominio;

import co.edu.uco.jjskafee.dominio.enums.EstadoVenta;
import co.edu.uco.jjskafee.dominio.enums.TipoProducto;
import co.edu.uco.jjskafee.dominio.excepciones.ReglaNegocioException;
import co.edu.uco.jjskafee.dominio.excepciones.SinPermisoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

    // ----- Máximo 50 líneas (productos base + adiciones) -----

    @Test
    void admiteHasta50Lineas() {
        Venta venta = Venta.crear(CAJERO, AHORA);
        for (int i = 0; i < 25; i++) {
            venta.agregar(LATTE, 1, Map.of(ALMENDRA, 1), ADICIONES_LATTE);   // 2 líneas cada vez
        }

        assertEquals(50, venta.getDetalles().size());
        assertDoesNotThrow(venta::validarParaRegistrar);
    }

    @Test
    void laLinea51SeRechazaYNoQuedaAgregada() {
        Venta venta = Venta.crear(CAJERO, AHORA);
        for (int i = 0; i < 50; i++) {
            venta.agregar(PANDEBONO, 1, Map.of(), Set.of());
        }

        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> venta.agregar(PANDEBONO, 1, Map.of(), Set.of()));
        assertTrue(e.getMessage().contains("50 líneas"), e.getMessage());
        assertEquals(50, venta.getDetalles().size());
    }

    @Test
    void lasAdicionesCuentanComoLineas() {
        Venta venta = Venta.crear(CAJERO, AHORA);
        for (int i = 0; i < 48; i++) {
            venta.agregar(PANDEBONO, 1, Map.of(), Set.of());
        }

        // 48 + latte + 2 adiciones = 51
        assertThrows(ReglaNegocioException.class,
                () -> venta.agregar(LATTE, 1, Map.of(ALMENDRA, 1, SHOT, 1), ADICIONES_LATTE));
        assertEquals(48, venta.getDetalles().size());
    }

    // ----- Precio mayor que 0 -----

    @Test
    void unProductoBaseConPrecio0NoSeVende() {
        Producto gratis = new Producto(UUID.randomUUID(), "Agua", BigDecimal.ZERO, TipoProducto.BASE, true);
        Venta venta = Venta.crear(CAJERO, AHORA);

        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> venta.agregar(gratis, 1, Map.of(), Set.of()));
        assertEquals("Agua no tiene un precio mayor que 0", e.getMessage());
    }

    @Test
    void unaAdicionConPrecio0NoSeVendeNiDejaElBaseAgregado() {
        Producto canela = new Producto(UUID.randomUUID(), "Canela", BigDecimal.ZERO, TipoProducto.ADICION, true);
        Venta venta = Venta.crear(CAJERO, AHORA);

        assertThrows(ReglaNegocioException.class,
                () -> venta.agregar(LATTE, 1, Map.of(canela, 1), Set.of(canela.getId())));
        assertTrue(venta.getDetalles().isEmpty());
    }

    // ----- Cantidad entre 1 y 99 -----

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 100})
    void laCantidadDelProductoBaseVaDe1A99(int cantidad) {
        Venta venta = Venta.crear(CAJERO, AHORA);

        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> venta.agregar(PANDEBONO, cantidad, Map.of(), Set.of()));
        assertEquals("La cantidad de Pandebono debe estar entre 1 y 99", e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 100})
    void laCantidadDeLaAdicionVaDe1A99(int cantidad) {
        Venta venta = Venta.crear(CAJERO, AHORA);

        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> venta.agregar(LATTE, 1, Map.of(SHOT, cantidad), ADICIONES_LATTE));
        assertEquals("La cantidad de Shot extra de espresso debe estar entre 1 y 99", e.getMessage());
    }

    @Test
    void aceptaLasCantidadesLimite1Y99() {
        Venta venta = Venta.crear(CAJERO, AHORA);
        venta.agregar(LATTE, 99, Map.of(SHOT, 99), ADICIONES_LATTE);
        venta.agregar(PANDEBONO, 1, Map.of(), Set.of());

        // 99×6500 + 99×2000 + 2500
        assertEquals(new BigDecimal("844000"), venta.total());
    }

    // ----- Total > 0, máximo 2 decimales y sin errores de redondeo -----

    @Test
    void elTotalConCentavosEsExactoSinRedondeo() {
        Producto diez = new Producto(UUID.randomUUID(), "Dulce", new BigDecimal("0.10"), TipoProducto.BASE, true);
        Producto veinte = new Producto(UUID.randomUUID(), "Galleta", new BigDecimal("0.20"), TipoProducto.BASE, true);
        Venta venta = Venta.crear(CAJERO, AHORA);
        venta.agregar(diez, 1, Map.of(), Set.of());
        venta.agregar(veinte, 1, Map.of(), Set.of());

        // Con double daría 0.30000000000000004
        assertEquals(0, new BigDecimal("0.30").compareTo(venta.total()));
        assertDoesNotThrow(venta::validarParaRegistrar);
    }

    @Test
    void elTotalDeMuchasLineasConCentavosNoAcumulaErrores() {
        Producto centavos = new Producto(UUID.randomUUID(), "Chicle", new BigDecimal("0.01"), TipoProducto.BASE, true);
        Venta venta = Venta.crear(CAJERO, AHORA);
        for (int i = 0; i < 50; i++) {
            venta.agregar(centavos, 99, Map.of(), Set.of());
        }

        assertEquals(0, new BigDecimal("49.50").compareTo(venta.total()));   // 50 × 99 × 0,01
    }

    @Test
    void unPrecioConMasDe2DecimalesSeRechazaEnVezDeRedondearse() {
        Producto raro = new Producto(UUID.randomUUID(), "Raro", new BigDecimal("1500.005"), TipoProducto.BASE, true);
        Venta venta = Venta.crear(CAJERO, AHORA);

        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> venta.agregar(raro, 1, Map.of(), Set.of()));
        assertEquals("El precio de Raro no puede tener más de 2 decimales", e.getMessage());
    }

    @Test
    void ceroALaDerechaNoCuentaComoDecimal() {
        Producto conCeros = new Producto(UUID.randomUUID(), "Tinto", new BigDecimal("2000.0000"), TipoProducto.BASE, true);
        Venta venta = Venta.crear(CAJERO, AHORA);

        assertDoesNotThrow(() -> venta.agregar(conCeros, 1, Map.of(), Set.of()));
    }

    @Test
    void unaVentaLeidaConTotal0NoSePuedeRegistrar() {
        // Un precio 0 que llegara por otra vía (p. ej. datos viejos) tampoco pasa la validación final
        Producto gratis = new Producto(UUID.randomUUID(), "Agua", BigDecimal.ZERO, TipoProducto.BASE, true);
        Venta venta = Venta.reconstruir(UUID.randomUUID(), CAFETERIA, CAJERO.id(), AHORA,
                List.of(new DetalleVenta(UUID.randomUUID(), gratis, 1, BigDecimal.ZERO, null)),
                null, null, EstadoVenta.CONFIRMADA, null);

        ReglaNegocioException e = assertThrows(ReglaNegocioException.class, venta::validarParaRegistrar);
        assertEquals("El total de la venta debe ser mayor que 0", e.getMessage());
    }

    // ----- Idempotencia -----

    @Test
    void guardaLaClaveYLaHuellaDeLaSolicitud() {
        UUID clave = UUID.randomUUID();
        Venta venta = Venta.crear(CAJERO, clave, "huella", AHORA);

        assertEquals(clave, venta.getClaveIdempotencia());
        assertEquals("huella", venta.getHashSolicitud());
        assertDoesNotThrow(() -> venta.verificarMismaSolicitud("huella"));
    }

    @Test
    void laMismaClaveConOtraSolicitudSeRechaza() {
        Venta venta = Venta.crear(CAJERO, UUID.randomUUID(), "huella", AHORA);

        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> venta.verificarMismaSolicitud("otra huella"));
        assertTrue(e.getMessage().contains("Idempotency-Key"), e.getMessage());
    }

    @Test
    void laClaveSinHuellaNoTieneSentido() {
        assertThrows(IllegalArgumentException.class, () -> Venta.crear(CAJERO, UUID.randomUUID(), null, AHORA));
    }
}
