package co.edu.uco.jjskafee.movimientos;

import co.edu.uco.jjskafee.compartido.dominio.excepciones.ReglaNegocioException;
import co.edu.uco.jjskafee.movimientos.aplicacion.ConsultarMovimientos;
import co.edu.uco.jjskafee.movimientos.aplicacion.puertos.salida.RepositorioMovimientos;
import co.edu.uco.jjskafee.movimientos.dominio.FiltroMovimientos;
import co.edu.uco.jjskafee.movimientos.dominio.MensajesMovimientos;
import co.edu.uco.jjskafee.movimientos.dominio.Movimiento;
import co.edu.uco.jjskafee.movimientos.dominio.PaginaMovimientos;
import co.edu.uco.jjskafee.movimientos.dominio.ResumenMovimientos;
import co.edu.uco.jjskafee.movimientos.dominio.TipoMovimiento;
import co.edu.uco.jjskafee.movimientos.dominio.Totales;
import co.edu.uco.jjskafee.soporte.Dobles;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static co.edu.uco.jjskafee.soporte.Dobles.ADMIN;
import static co.edu.uco.jjskafee.soporte.Dobles.AHORA;
import static co.edu.uco.jjskafee.soporte.Dobles.CAJERO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MovimientosTest {

    // ---------------- FiltroMovimientos ----------------

    @Test
    void lasFechasSonDiasCompletosEnUtc() {
        FiltroMovimientos filtro = FiltroMovimientos.crear(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 14),
                null, null, null, null);
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), filtro.inicioUtc());
        assertEquals(Instant.parse("2026-10-15T00:00:00Z"), filtro.finUtcExclusivo());
        assertEquals(0, filtro.pagina());
        assertEquals(FiltroMovimientos.TAMANO_POR_DEFECTO, filtro.tamano());
    }

    @Test
    void desdeNoPuedeSerPosteriorAHasta() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () -> FiltroMovimientos.crear(
                LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 1), null, null, null, null));
        assertEquals(MensajesMovimientos.FECHAS_INVERTIDAS, error.getCodigo());
    }

    @Test
    void elTamanoDePaginaTieneLimite() {
        assertEquals(MensajesMovimientos.TAMANO_INVALIDO, assertThrows(ReglaNegocioException.class,
                () -> FiltroMovimientos.crear(null, null, null, null, 0, FiltroMovimientos.TAMANO_MAXIMO + 1)).getCodigo());
        assertEquals(MensajesMovimientos.PAGINA_INVALIDA, assertThrows(ReglaNegocioException.class,
                () -> FiltroMovimientos.crear(null, null, null, null, -1, 10)).getCodigo());
    }

    // ---------------- ConsultarMovimientos ----------------

    @Test
    void elAdministradorVeTodoYElCajeroSoloLoSuyo() {
        RepositorioFalso repositorio = new RepositorioFalso();
        ConsultarMovimientos casoUso = new ConsultarMovimientos(repositorio, new Dobles.UnidadDeTrabajoEnMemoria(),
                new Dobles.MensajesPorClave());
        FiltroMovimientos filtro = FiltroMovimientos.crear(null, null, null, null, null, null);

        casoUso.consultar(filtro, ADMIN);
        assertNull(repositorio.ultimoUsuario);

        casoUso.consultar(filtro, CAJERO);
        assertEquals(CAJERO.id(), repositorio.ultimoUsuario);
    }

    @Test
    void laDescripcionDeLasVentasSaleDeLosMensajes() {
        ConsultarMovimientos casoUso = new ConsultarMovimientos(new RepositorioFalso(),
                new Dobles.UnidadDeTrabajoEnMemoria(), new Dobles.MensajesPorClave());

        PaginaMovimientos pagina = casoUso.consultar(FiltroMovimientos.crear(null, null, null, null, null, null), ADMIN);

        assertEquals(MensajesMovimientos.DESCRIPCION_VENTA.clave(), pagina.movimientos().get(0).descripcion());
        assertEquals("Factura de energía", pagina.movimientos().get(1).descripcion());
        assertEquals(0, new BigDecimal("-138500").compareTo(pagina.totales().balance()));
        assertEquals(1, pagina.totalPaginas());
    }

    private static final class RepositorioFalso implements RepositorioMovimientos {
        UUID ultimoUsuario;

        @Override
        public List<Movimiento> buscar(UUID idCafeteria, FiltroMovimientos filtro, UUID soloDelUsuario) {
            ultimoUsuario = soloDelUsuario;
            return List.of(
                    new Movimiento(UUID.randomUUID(), TipoMovimiento.INGRESO, AHORA, new BigDecimal("26500"),
                            "Venta", null, "Andrés Ruiz"),
                    new Movimiento(UUID.randomUUID(), TipoMovimiento.EGRESO, AHORA, new BigDecimal("165000"),
                            "Factura de energía", "Servicios públicos", "Laura Gómez"));
        }

        @Override
        public ResumenMovimientos resumir(UUID idCafeteria, FiltroMovimientos filtro, UUID soloDelUsuario) {
            return new ResumenMovimientos(new Totales(new BigDecimal("26500"), new BigDecimal("165000")), 2);
        }
    }
}
