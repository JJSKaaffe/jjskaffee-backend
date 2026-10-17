package co.edu.uco.jjskafee.servicios.casouso;

import co.edu.uco.jjskafee.dominio.FiltroMovimientos;
import co.edu.uco.jjskafee.dominio.Movimiento;
import co.edu.uco.jjskafee.dominio.enums.TipoMovimiento;
import co.edu.uco.jjskafee.dominio.excepciones.ReglaNegocioException;
import co.edu.uco.jjskafee.servicios.input.dto.request.FiltroMovimientosRequest;
import co.edu.uco.jjskafee.servicios.input.dto.response.MovimientosResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static co.edu.uco.jjskafee.DatosPrueba.*;
import static org.junit.jupiter.api.Assertions.*;

class ConsultarMovimientosTest {

    private static final FiltroMovimientosRequest SIN_FILTROS = new FiltroMovimientosRequest(null, null, null, null, null, null);

    private final RepositoriosEnMemoria.Movimientos repo = new RepositoriosEnMemoria.Movimientos();
    private final RepositoriosEnMemoria.UnidadDeTrabajoDirecta uow = new RepositoriosEnMemoria.UnidadDeTrabajoDirecta();
    private final ConsultarMovimientos casoDeUso = new ConsultarMovimientos(repo, uow);

    private void cargarDatos() {
        repo.datos.add(new Movimiento(UUID.randomUUID(), TipoMovimiento.INGRESO, AHORA, new BigDecimal("14500"), "Venta", null, "Andrés Ruiz"));
        repo.datos.add(new Movimiento(UUID.randomUUID(), TipoMovimiento.INGRESO, AHORA, new BigDecimal("12000"), "Venta", null, "Andrés Ruiz"));
        repo.datos.add(new Movimiento(UUID.randomUUID(), TipoMovimiento.EGRESO, AHORA, new BigDecimal("45000"), "Leche", "Insumos", "Laura Gómez"));
        // Los totales los calcula la BD sobre todo el filtro, no sobre la página
        repo.totalIngresos = new BigDecimal("26500");
        repo.totalEgresos = new BigDecimal("45000");
        repo.totalMovimientos = 3;
    }

    @Test
    void devuelveLosTotalesDeLaBaseDeDatosYElBalance() {
        cargarDatos();
        MovimientosResponse r = casoDeUso.consultar(SIN_FILTROS, ADMIN);

        assertEquals(3, r.movimientos().size());
        assertEquals(new BigDecimal("26500"), r.totales().ingresos());
        assertEquals(new BigDecimal("45000"), r.totales().egresos());
        assertEquals(new BigDecimal("-18500"), r.totales().balance());
        assertEquals(3, r.totalMovimientos());
        assertEquals(CAFETERIA, uow.ultimaCafeteria);
    }

    @Test
    void losTotalesSonDeTodoElFiltroAunqueLaPaginaTraigaMenos() {
        cargarDatos();
        MovimientosResponse r = casoDeUso.consultar(new FiltroMovimientosRequest(null, null, null, null, 1, 2), ADMIN);

        assertEquals(1, r.movimientos().size());
        assertEquals(1, r.pagina());
        assertEquals(2, r.tamano());
        assertEquals(3, r.totalMovimientos());
        assertEquals(new BigDecimal("26500"), r.totales().ingresos());
    }

    @Test
    void sinPaginaNiTamanoUsaLaPrimeraPaginaConElTamanoPorDefecto() {
        casoDeUso.consultar(SIN_FILTROS, ADMIN);

        assertEquals(0, repo.ultimoFiltro.pagina());
        assertEquals(FiltroMovimientos.TAMANO_POR_DEFECTO, repo.ultimoFiltro.tamano());
    }

    @Test
    void pasaLosFiltrosDeFechaTipoYCategoriaAlRepositorio() {
        LocalDate desde = LocalDate.of(2026, 10, 1);
        LocalDate hasta = LocalDate.of(2026, 10, 31);
        casoDeUso.consultar(new FiltroMovimientosRequest(desde, hasta, TipoMovimiento.EGRESO, INSUMOS.getId(), null, null), ADMIN);

        assertEquals(desde, repo.ultimoFiltro.desde());
        assertEquals(hasta, repo.ultimoFiltro.hasta());
        assertEquals(TipoMovimiento.EGRESO, repo.ultimoFiltro.tipo());
        assertEquals(INSUMOS.getId(), repo.ultimoFiltro.idCategoria());
    }

    @Test
    void elAdministradorVeTodoYElCajeroSoloLoSuyo() {
        casoDeUso.consultar(SIN_FILTROS, ADMIN);
        assertNull(repo.ultimoFiltro.soloDelUsuario());

        casoDeUso.consultar(SIN_FILTROS, CAJERO);
        assertEquals(CAJERO.id(), repo.ultimoFiltro.soloDelUsuario());
    }

    @Test
    void rechazaUnRangoDeFechasAlReves() {
        FiltroMovimientosRequest alReves = new FiltroMovimientosRequest(
                LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 1), null, null, null, null);
        assertThrows(ReglaNegocioException.class, () -> casoDeUso.consultar(alReves, ADMIN));
        assertNull(uow.ultimaCafeteria);   // no llegó a consultar la BD
    }
}
