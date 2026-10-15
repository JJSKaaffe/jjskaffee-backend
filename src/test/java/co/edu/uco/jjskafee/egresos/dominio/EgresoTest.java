package co.edu.uco.jjskafee.egresos.dominio;

import co.edu.uco.jjskafee.compartido.dominio.excepciones.ReglaNegocioException;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.SinPermisoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static co.edu.uco.jjskafee.soporte.Dobles.ADMIN;
import static co.edu.uco.jjskafee.soporte.Dobles.AHORA;
import static co.edu.uco.jjskafee.soporte.Dobles.CAJERO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EgresoTest {

    private static final CategoriaEgreso INSUMOS = new CategoriaEgreso(UUID.randomUUID(), "Insumos", true, false);
    private static final CategoriaEgreso ARCHIVADA = new CategoriaEgreso(UUID.randomUUID(), "Antigua", true, true);
    private static final CategoriaEgreso DE_MENU = new CategoriaEgreso(UUID.randomUUID(), "Bebidas", false, false);

    @Test
    void elAdministradorRegistraUnEgresoValido() {
        Egreso egreso = Egreso.registrar(ADMIN, INSUMOS, new BigDecimal("45000"), "  Compra de leche  ", " ", AHORA);

        assertEquals(ADMIN.idCafeteria(), egreso.getIdCafeteria());
        assertEquals(ADMIN.id(), egreso.getRegistradoPor());
        assertEquals("Compra de leche", egreso.getDescripcion());
        assertNull(egreso.getComprobante());
        assertEquals(AHORA, egreso.getFechaHora());
    }

    @Test
    void elCajeroNoRegistraEgresos() {
        SinPermisoException error = assertThrows(SinPermisoException.class,
                () -> Egreso.registrar(CAJERO, INSUMOS, BigDecimal.TEN, "x", null, AHORA));
        assertEquals(MensajesEgresos.SOLO_ADMINISTRADOR, error.getCodigo());
    }

    @Test
    void laCategoriaDebeSerDeEgresoYEstarActiva() {
        for (CategoriaEgreso categoria : new CategoriaEgreso[]{ARCHIVADA, DE_MENU, null}) {
            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> Egreso.registrar(ADMIN, categoria, BigDecimal.TEN, "x", null, AHORA));
            assertEquals(MensajesEgresos.CATEGORIA_INVALIDA, error.getCodigo());
        }
    }

    @Test
    void elMontoDebeSerMayorQueCero() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> Egreso.registrar(ADMIN, INSUMOS, BigDecimal.ZERO, "x", null, AHORA));
        assertEquals(MensajesEgresos.MONTO_INVALIDO, error.getCodigo());
    }

    @Test
    void laDescripcionEsObligatoriaYTieneMaximo200() {
        assertEquals(MensajesEgresos.DESCRIPCION_INVALIDA, assertThrows(ReglaNegocioException.class,
                () -> Egreso.registrar(ADMIN, INSUMOS, BigDecimal.TEN, "   ", null, AHORA)).getCodigo());
        assertEquals(MensajesEgresos.DESCRIPCION_INVALIDA, assertThrows(ReglaNegocioException.class,
                () -> Egreso.registrar(ADMIN, INSUMOS, BigDecimal.TEN, "x".repeat(201), null, AHORA)).getCodigo());
    }

    @Test
    void elComprobanteTieneMaximo50() {
        assertEquals(MensajesEgresos.COMPROBANTE_INVALIDO, assertThrows(ReglaNegocioException.class,
                () -> Egreso.registrar(ADMIN, INSUMOS, BigDecimal.TEN, "x", "c".repeat(51), AHORA)).getCodigo());
    }
}
