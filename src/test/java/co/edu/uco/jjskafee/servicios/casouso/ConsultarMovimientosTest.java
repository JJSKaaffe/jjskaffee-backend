package co.edu.uco.jjskafee.servicios.casouso;

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

    private final RepositoriosEnMemoria.Movimientos repo = new RepositoriosEnMemoria.Movimientos();
    private final ConsultarMovimientos casoDeUso =
            new ConsultarMovimientos(repo, new RepositoriosEnMemoria.UnidadDeTrabajoDirecta());

    private void cargarDatos() {
        repo.datos.add(new Movimiento(UUID.randomUUID(), TipoMovimiento.INGRESO, AHORA, new BigDecimal("14500"), "Venta", null, "Andrés Ruiz"));
        repo.datos.add(new Movimiento(UUID.randomUUID(), TipoMovimiento.INGRESO, AHORA, new BigDecimal("12000"), "Venta", null, "Andrés Ruiz"));
        repo.datos.add(new Movimiento(UUID.randomUUID(), TipoMovimiento.EGRESO, AHORA, new BigDecimal("45000"), "Leche", "Insumos", "Laura Gómez"));
    }

    @Test
    void calculaIngresosEgresosYBalance() {
        cargarDatos();
        MovimientosResponse r = casoDeUso.consultar(new FiltroMovimientosRequest(null, null, null), ADMIN);

        assertEquals(3, r.movimientos().size());
        assertEquals(new BigDecimal("26500"), r.totales().ingresos());
        assertEquals(new BigDecimal("45000"), r.totales().egresos());
        assertEquals(new BigDecimal("-18500"), r.totales().balance());
    }

    @Test
    void elAdministradorVeTodoYElCajeroSoloLoSuyo() {
        casoDeUso.consultar(new FiltroMovimientosRequest(null, null, null), ADMIN);
        assertNull(repo.ultimoFiltroUsuario);

        casoDeUso.consultar(new FiltroMovimientosRequest(null, null, null), CAJERO);
        assertEquals(CAJERO.id(), repo.ultimoFiltroUsuario);
    }

    @Test
    void rechazaUnRangoDeFechasAlReves() {
        FiltroMovimientosRequest alReves = new FiltroMovimientosRequest(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 1), null);
        assertThrows(ReglaNegocioException.class, () -> casoDeUso.consultar(alReves, ADMIN));
    }
}
