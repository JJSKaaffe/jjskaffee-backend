package co.edu.uco.jjskafee.movimientos.dominio;

import co.edu.uco.jjskafee.compartido.dominio.excepciones.ReglaNegocioException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class FiltroMovimientosTest {

    private static FiltroMovimientos pagina(int pagina, int tamano) {
        return new FiltroMovimientos(null, null, null, null, null, pagina, tamano);
    }

    @Test
    void calculaCuantosRegistrosSaltar() {
        assertEquals(0, pagina(0, 20).desplazamiento());
        assertEquals(100, pagina(2, 50).desplazamiento());
    }

    @Test
    void elMismoDiaEnDesdeYHastaEsValido() {
        LocalDate hoy = LocalDate.of(2026, 10, 11);
        assertDoesNotThrow(() -> new FiltroMovimientos(hoy, hoy, null, null, null, 0, 20));
    }

    @Test
    void rechazaDesdePosteriorAHasta() {
        assertThrows(ReglaNegocioException.class, () -> new FiltroMovimientos(
                LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 1), null, null, null, 0, 20));
    }

    @Test
    void rechazaPaginaNegativa() {
        assertThrows(ReglaNegocioException.class, () -> pagina(-1, 20));
    }

    @Test
    void elTamanoVaDe1A50() {
        assertThrows(ReglaNegocioException.class, () -> pagina(0, 0));
        assertThrows(ReglaNegocioException.class, () -> pagina(0, FiltroMovimientos.TAMANO_MAXIMO + 1));
        assertDoesNotThrow(() -> pagina(0, FiltroMovimientos.TAMANO_MAXIMO));
    }
}
