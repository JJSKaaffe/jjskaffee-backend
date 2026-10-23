package co.edu.uco.jjskafee.ingresos.dominio;

import co.edu.uco.jjskafee.compartido.dominio.Categoria;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.ReglaNegocioException;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.SinPermisoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static co.edu.uco.jjskafee.DatosPrueba.*;
import static org.junit.jupiter.api.Assertions.*;

class IngresoLibreTest {

    private static IngresoLibre registrar(String monto) {
        return IngresoLibre.registrar(ADMIN, EVENTOS, new BigDecimal(monto), "Cumpleaños de 20 personas", null, AHORA);
    }

    private static void rechazaMonto(String monto, String mensaje) {
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class, () -> registrar(monto));
        assertEquals(mensaje, e.getMessage());
    }

    @Test
    void elAdministradorRegistraUnIngresoLibre() {
        IngresoLibre ingreso = IngresoLibre.registrar(ADMIN, EVENTOS, new BigDecimal("150000.50"),
                "  Cumpleaños de 20 personas  ", " FAC-77 ", AHORA);

        assertNotNull(ingreso.getId());
        assertEquals(new BigDecimal("150000.50"), ingreso.getMonto());
        assertEquals("Cumpleaños de 20 personas", ingreso.getDescripcion());
        assertEquals("FAC-77", ingreso.getComprobante());
        assertSame(EVENTOS, ingreso.getCategoria());
        assertFalse(ingreso.isAnulado());
    }

    @Test
    void laCafeteriaElUsuarioYLaFechaLosPoneElServidor() {
        IngresoLibre ingreso = registrar("1000");

        assertEquals(ADMIN.idCafeteria(), ingreso.getIdCafeteria());
        assertEquals(ADMIN.id(), ingreso.getRegistradoPor());
        assertEquals(AHORA, ingreso.getFechaHora());
    }

    @Test
    void elCajeroNoPuedeRegistrarIngresosLibres() {
        SinPermisoException e = assertThrows(SinPermisoException.class, () -> IngresoLibre.registrar(
                CAJERO, EVENTOS, new BigDecimal("1000"), "Evento", null, AHORA));
        assertEquals("Solo el administrador puede registrar ingresos libres", e.getMessage());
        // Laura es administradora en A pero cajera en B: en B tampoco puede
        assertThrows(SinPermisoException.class, () -> IngresoLibre.registrar(
                LAURA_EN_B, EVENTOS, new BigDecimal("1000"), "Evento", null, AHORA));
    }

    @Test
    void laCategoriaDebeSerDeIngresoYEstarActiva() {
        for (Categoria categoria : new Categoria[]{INSUMOS, BEBIDAS, CONVENIOS_ARCHIVADA, null}) {
            ReglaNegocioException e = assertThrows(ReglaNegocioException.class, () -> IngresoLibre.registrar(
                    ADMIN, categoria, new BigDecimal("1000"), "Evento", null, AHORA));
            assertEquals("La categoría debe ser de ingreso y estar activa", e.getMessage());
        }
    }

    @Test
    void elMontoDebeSerMayorQueCero() {
        rechazaMonto("0", "El monto debe ser mayor que 0");
        rechazaMonto("0.00", "El monto debe ser mayor que 0");
        rechazaMonto("-5000", "El monto debe ser mayor que 0");
        assertThrows(ReglaNegocioException.class, () -> IngresoLibre.registrar(
                ADMIN, EVENTOS, null, "Evento", null, AHORA));
    }

    @Test
    void unMontoConMasDeDosDecimalesSeRechazaSinRedondear() {
        rechazaMonto("1000.005", "El monto no puede tener más de 2 decimales");
        rechazaMonto("0.001", "El monto no puede tener más de 2 decimales");
        // Ceros a la derecha no cuentan como decimales: 1000.500 es 1000.5
        assertEquals(new BigDecimal("1000.500"), registrar("1000.500").getMonto());
        assertEquals(new BigDecimal("0.01"), registrar("0.01").getMonto());
    }

    @Test
    void elMontoTieneMaximoDiezDigitosEnteros() {
        assertEquals(new BigDecimal("9999999999.99"), registrar("9999999999.99").getMonto());
        rechazaMonto("10000000000", "El monto no puede tener más de 10 dígitos enteros");
        rechazaMonto("1E+10", "El monto no puede tener más de 10 dígitos enteros");
    }

    @Test
    void laDescripcionEsObligatoriaYTieneMaximo200() {
        for (String descripcion : new String[]{null, "", "   ", "x".repeat(201)}) {
            assertThrows(ReglaNegocioException.class, () -> IngresoLibre.registrar(
                    ADMIN, EVENTOS, new BigDecimal("1000"), descripcion, null, AHORA));
        }
        assertEquals(200, IngresoLibre.registrar(ADMIN, EVENTOS, new BigDecimal("1000"), "x".repeat(200), null, AHORA)
                .getDescripcion().length());
    }

    @Test
    void elComprobanteEsOpcionalYTieneMaximo50() {
        assertNull(IngresoLibre.registrar(ADMIN, EVENTOS, new BigDecimal("1000"), "Evento", "   ", AHORA).getComprobante());
        assertThrows(ReglaNegocioException.class, () -> IngresoLibre.registrar(
                ADMIN, EVENTOS, new BigDecimal("1000"), "Evento", "x".repeat(51), AHORA));
    }

    @Test
    void laClaveDeIdempotenciaYLaHuellaVanJuntas() {
        assertThrows(IllegalArgumentException.class, () -> IngresoLibre.registrar(
                ADMIN, EVENTOS, new BigDecimal("1000"), "Evento", null, UUID.randomUUID(), null, AHORA));
        assertThrows(IllegalArgumentException.class, () -> IngresoLibre.registrar(
                ADMIN, EVENTOS, new BigDecimal("1000"), "Evento", null, null, "abc", AHORA));
    }

    @Test
    void unReintentoConOtraHuellaSeRechaza() {
        IngresoLibre ingreso = IngresoLibre.registrar(ADMIN, EVENTOS, new BigDecimal("1000"), "Evento", null,
                UUID.randomUUID(), "huella-1", AHORA);

        assertDoesNotThrow(() -> ingreso.verificarMismaSolicitud("huella-1"));
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> ingreso.verificarMismaSolicitud("huella-2"));
        assertTrue(e.getMessage().startsWith("Esta Idempotency-Key ya se usó para un ingreso libre"));
    }
}
