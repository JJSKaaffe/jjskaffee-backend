package co.edu.uco.jjskafee.egresos.dominio;

import co.edu.uco.jjskafee.compartido.dominio.excepciones.ReglaNegocioException;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.SinPermisoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static co.edu.uco.jjskafee.DatosPrueba.*;
import static org.junit.jupiter.api.Assertions.*;

class EgresoTest {

    @Test
    void elAdministradorRegistraUnEgresoValido() {
        Egreso egreso = Egreso.registrar(ADMIN, INSUMOS, new BigDecimal("45000"), "  Compra de leche  ", "FAC-1023", AHORA);

        assertEquals(new BigDecimal("45000"), egreso.getMonto());
        assertEquals("Compra de leche", egreso.getDescripcion());   // quita espacios
        assertEquals(ADMIN.id(), egreso.getRegistradoPor());        // lo pone el servidor
        assertEquals(CAFETERIA, egreso.getIdCafeteria());
        assertEquals(AHORA, egreso.getFechaHora());
    }

    @Test
    void elCajeroNoPuedeRegistrarEgresos() {
        assertThrows(SinPermisoException.class,
                () -> Egreso.registrar(CAJERO, INSUMOS, new BigDecimal("1000"), "x", null, AHORA));
    }

    @Test
    void elMontoDebeSerMayorQueCero() {
        assertThrows(ReglaNegocioException.class,
                () -> Egreso.registrar(ADMIN, INSUMOS, BigDecimal.ZERO, "x", null, AHORA));
        assertThrows(ReglaNegocioException.class,
                () -> Egreso.registrar(ADMIN, INSUMOS, new BigDecimal("-5"), "x", null, AHORA));
    }

    @Test
    void noAceptaCategoriasArchivadasNiDeOtroTipo() {
        assertThrows(ReglaNegocioException.class,
                () -> Egreso.registrar(ADMIN, ARCHIVADA, new BigDecimal("1000"), "x", null, AHORA));
        assertThrows(ReglaNegocioException.class,
                () -> Egreso.registrar(ADMIN, BEBIDAS, new BigDecimal("1000"), "x", null, AHORA));
    }

    @Test
    void laDescripcionEsObligatoria() {
        assertThrows(ReglaNegocioException.class,
                () -> Egreso.registrar(ADMIN, INSUMOS, new BigDecimal("1000"), "   ", null, AHORA));
    }

    @Test
    void elComprobanteVacioQuedaNulo() {
        Egreso egreso = Egreso.registrar(ADMIN, INSUMOS, new BigDecimal("1000"), "x", "  ", AHORA);
        assertNull(egreso.getComprobante());
    }
}
