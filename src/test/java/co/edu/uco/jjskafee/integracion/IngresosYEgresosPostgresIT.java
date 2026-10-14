package co.edu.uco.jjskafee.integracion;

import co.edu.uco.jjskafee.dominio.DetalleVenta;
import co.edu.uco.jjskafee.dominio.Egreso;
import co.edu.uco.jjskafee.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.dominio.Venta;
import co.edu.uco.jjskafee.dominio.enums.EstadoVenta;
import co.edu.uco.jjskafee.dominio.enums.TipoMovimiento;
import co.edu.uco.jjskafee.servicios.input.dto.request.AdicionRequest;
import co.edu.uco.jjskafee.servicios.input.dto.request.EgresoRequest;
import co.edu.uco.jjskafee.servicios.input.dto.request.FiltroMovimientosRequest;
import co.edu.uco.jjskafee.servicios.input.dto.request.IngresoRequest;
import co.edu.uco.jjskafee.servicios.input.dto.request.ItemRequest;
import co.edu.uco.jjskafee.servicios.input.dto.response.EgresoResponse;
import co.edu.uco.jjskafee.servicios.input.dto.response.IngresoResponse;
import co.edu.uco.jjskafee.servicios.input.dto.response.MovimientoResponse;
import co.edu.uco.jjskafee.servicios.input.dto.response.MovimientosResponse;
import co.edu.uco.jjskafee.servicios.input.ports.ConsultarMovimientosUseCase;
import co.edu.uco.jjskafee.servicios.input.ports.RegistrarEgresoUseCase;
import co.edu.uco.jjskafee.servicios.input.ports.RegistrarIngresoUseCase;
import co.edu.uco.jjskafee.servicios.output.ports.EgresoRepository;
import co.edu.uco.jjskafee.servicios.output.ports.UnidadDeTrabajo;
import co.edu.uco.jjskafee.servicios.output.ports.VentaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static co.edu.uco.jjskafee.DatosPrueba.*;
import static co.edu.uco.jjskafee.integracion.ConfiguracionDePrueba.AHORA_PRUEBA;
import static co.edu.uco.jjskafee.integracion.ConfiguracionDePrueba.DIA_PRUEBA;
import static org.junit.jupiter.api.Assertions.*;

/** Casos de uso completos contra PostgreSQL (RF14, RF17, RF24–RF27, RF38, RF43). Todo se deshace al final. */
@PruebaDeIntegracion
@Transactional
class IngresosYEgresosPostgresIT {

    @Autowired private RegistrarIngresoUseCase registrarIngreso;
    @Autowired private RegistrarEgresoUseCase registrarEgreso;
    @Autowired private ConsultarMovimientosUseCase consultarMovimientos;
    @Autowired private UnidadDeTrabajo unidadDeTrabajo;
    @Autowired private VentaRepository ventas;
    @Autowired private EgresoRepository egresos;
    @Autowired private JdbcTemplate jdbc;

    private static FiltroMovimientosRequest delDia(TipoMovimiento tipo, UUID idCategoria, Integer pagina, Integer tamano) {
        return new FiltroMovimientosRequest(DIA_PRUEBA, DIA_PRUEBA, tipo, idCategoria, pagina, tamano);
    }

    private IngresoResponse vender(int cantidadPandebonos, UsuarioAutenticado quien) {
        return registrarIngreso.registrar(
                new IngresoRequest(List.of(new ItemRequest(PANDEBONO.getId(), cantidadPandebonos, null))), quien);
    }

    private EgresoResponse gastar(UUID idCategoria, String monto) {
        return registrarEgreso.registrar(new EgresoRequest(idCategoria, new BigDecimal(monto), "Gasto de prueba", null), ADMIN);
    }

    @Test
    void guardaLaVentaConSusAdicionesYLaLeeIgual() {
        // 2 lattes con leche de almendra (x1) y shot extra (x2) + 1 pandebono
        IngresoResponse r = registrarIngreso.registrar(new IngresoRequest(List.of(
                new ItemRequest(LATTE.getId(), 2, List.of(new AdicionRequest(ALMENDRA.getId(), 1),
                                                          new AdicionRequest(SHOT.getId(), 2))),
                new ItemRequest(PANDEBONO.getId(), 1, null))), CAJERO);

        BigDecimal esperado = new BigDecimal("21000");   // 2×6500 + 1500 + 2×2000 + 2500
        assertEquals(0, esperado.compareTo(r.total()));
        assertEquals(AHORA_PRUEBA, r.fechaHora());          // la fecha la pone el servidor (RF43)
        assertEquals(CAJERO.id(), r.registradoPor());

        Venta leida = unidadDeTrabajo.ejecutar(CAFETERIA, () -> ventas.buscarPorId(r.idVenta())).orElseThrow();
        assertEquals(4, leida.getDetalles().size());
        assertEquals(0, esperado.compareTo(leida.total()));
        assertEquals(EstadoVenta.CONFIRMADA, leida.getEstado());
        DetalleVenta almendra = leida.getDetalles().stream()
                .filter(d -> d.getProducto().getId().equals(ALMENDRA.getId())).findFirst().orElseThrow();
        assertEquals(LATTE.getId(), almendra.getDetalleBase().getProducto().getId());   // la adición quedó unida a su base
        assertEquals(0, new BigDecimal("1500").compareTo(almendra.getPrecioUnitario()));

        // El total quedó guardado en la venta para sumarlo con SQL (ADR-0004)
        BigDecimal totalEnBd = unidadDeTrabajo.ejecutar(CAFETERIA, () -> jdbc.queryForObject(
                "SELECT total FROM venta WHERE id = ?", BigDecimal.class, r.idVenta()));
        assertEquals(0, esperado.compareTo(totalEnBd));
    }

    @Test
    void deshacerLaVentaLaAnulaYDejaDeContar() {
        IngresoResponse r = vender(1, CAJERO);

        registrarIngreso.deshacer(r.idVenta(), CAJERO);

        Venta leida = unidadDeTrabajo.ejecutar(CAFETERIA, () -> ventas.buscarPorId(r.idVenta())).orElseThrow();
        assertEquals(EstadoVenta.ANULADA, leida.getEstado());
        assertNotNull(leida.getMotivoAnulacion());
        MovimientosResponse m = consultarMovimientos.consultar(delDia(null, null, null, null), ADMIN);
        assertTrue(m.movimientos().stream().noneMatch(x -> x.idMovimiento().equals(r.idVenta())));
        assertEquals(0, BigDecimal.ZERO.compareTo(m.totales().ingresos()));
    }

    @Test
    void elEgresoApareceConSuCategoriaYQuienLoRegistro() {
        EgresoResponse e = gastar(INSUMOS.getId(), "38000.50");

        MovimientoResponse m = consultarMovimientos.consultar(delDia(null, null, null, null), ADMIN)
                .movimientos().getFirst();
        assertEquals(e.idEgreso(), m.idMovimiento());
        assertEquals("EGRESO", m.tipo());
        assertEquals("Insumos", m.categoria());
        assertEquals("Laura Gómez", m.registradoPor());
        assertEquals(0, new BigDecimal("38000.50").compareTo(m.monto()));
        assertEquals(AHORA_PRUEBA, m.fechaHora());
    }

    @Test
    void losTotalesSonDeTodoElFiltroYLaListaSePagina() {
        vender(1, CAJERO);    //  2.500
        vender(2, CAJERO);    //  5.000
        vender(4, ADMIN);     // 10.000
        gastar(INSUMOS.getId(), "30000");
        gastar(SERVICIOS.getId(), "12000");

        MovimientosResponse pagina0 = consultarMovimientos.consultar(delDia(null, null, 0, 2), ADMIN);
        MovimientosResponse pagina2 = consultarMovimientos.consultar(delDia(null, null, 2, 2), ADMIN);

        assertEquals(2, pagina0.movimientos().size());
        assertEquals(1, pagina2.movimientos().size());
        assertEquals(5, pagina0.totalMovimientos());
        assertEquals(0, new BigDecimal("17500").compareTo(pagina0.totales().ingresos()));
        assertEquals(0, new BigDecimal("42000").compareTo(pagina0.totales().egresos()));
        assertEquals(0, new BigDecimal("-24500").compareTo(pagina0.totales().balance()));
    }

    @Test
    void filtraPorTipoYPorCategoria() {
        vender(1, CAJERO);
        gastar(INSUMOS.getId(), "30000");
        gastar(SERVICIOS.getId(), "12000");

        MovimientosResponse soloEgresos = consultarMovimientos.consultar(delDia(TipoMovimiento.EGRESO, null, null, null), ADMIN);
        MovimientosResponse soloServicios = consultarMovimientos.consultar(delDia(null, SERVICIOS.getId(), null, null), ADMIN);

        assertEquals(2, soloEgresos.totalMovimientos());
        assertEquals(0, BigDecimal.ZERO.compareTo(soloEgresos.totales().ingresos()));
        assertEquals(1, soloServicios.totalMovimientos());
        assertEquals(0, new BigDecimal("12000").compareTo(soloServicios.totales().egresos()));
    }

    @Test
    void elCajeroSoloConsultaLoQueElRegistro() {
        vender(1, CAJERO);
        vender(4, ADMIN);
        gastar(INSUMOS.getId(), "30000");

        MovimientosResponse delCajero = consultarMovimientos.consultar(delDia(null, null, null, null), CAJERO);

        assertEquals(1, delCajero.totalMovimientos());
        assertEquals("Andrés Ruiz", delCajero.movimientos().getFirst().registradoPor());
        assertEquals(0, BigDecimal.ZERO.compareTo(delCajero.totales().egresos()));
    }

    @Test
    void elFiltroDeFechasUsaElDiaCompletoEnHoraDeColombia() {
        // 2099-01-16 03:00 UTC = 2099-01-15 22:00 en Colombia: pertenece al día 15, no al 16
        Instant nocheEnColombia = Instant.parse("2099-01-16T03:00:00Z");
        Egreso tarde = Egreso.registrar(ADMIN, INSUMOS, new BigDecimal("9000"), "Cierre de caja", null, nocheEnColombia);
        unidadDeTrabajo.ejecutar(CAFETERIA, () -> { egresos.guardar(tarde); return null; });

        assertEquals(1, contar(DIA_PRUEBA, DIA_PRUEBA));
        assertEquals(1, contar(null, DIA_PRUEBA));
        assertEquals(0, contar(DIA_PRUEBA.plusDays(1), null));
        assertEquals(0, contar(DIA_PRUEBA.minusDays(1), DIA_PRUEBA.minusDays(1)));
    }

    private long contar(LocalDate desde, LocalDate hasta) {
        return consultarMovimientos.consultar(
                new FiltroMovimientosRequest(desde, hasta, null, null, null, null), ADMIN).totalMovimientos();
    }
}
