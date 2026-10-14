package co.edu.uco.jjskafee.integracion;

import co.edu.uco.jjskafee.dominio.Egreso;
import co.edu.uco.jjskafee.dominio.Producto;
import co.edu.uco.jjskafee.dominio.Venta;
import co.edu.uco.jjskafee.dominio.enums.TipoProducto;
import co.edu.uco.jjskafee.infraestructura.output.repositorios.estructurados.PgUnidadDeTrabajo;
import co.edu.uco.jjskafee.servicios.output.ports.EgresoRepository;
import co.edu.uco.jjskafee.servicios.output.ports.UnidadDeTrabajo;
import co.edu.uco.jjskafee.servicios.output.ports.VentaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.Map;
import java.util.UUID;

import static co.edu.uco.jjskafee.DatosPrueba.*;
import static co.edu.uco.jjskafee.integracion.ConfiguracionDePrueba.AHORA_PRUEBA;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ADR-0002: un movimiento se guarda completo o no se guarda nada.
 * Estas pruebas NO usan @Transactional: confirman lo que de verdad queda en la BD
 * (y como todo falla a propósito, no dejan datos).
 */
@PruebaDeIntegracion
class TransaccionTodoONadaIT {

    @Autowired private UnidadDeTrabajo unidadDeTrabajo;
    @Autowired private VentaRepository ventas;
    @Autowired private EgresoRepository egresos;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;

    @Test
    void siFallaUnDetalleNoQuedaNiLaVentaNiLosOtrosDetalles() {
        // Un producto que no existe en la BD: su detalle viola la llave foránea
        Producto fantasma = new Producto(UUID.randomUUID(), "No existe", new BigDecimal("1000"), TipoProducto.BASE, true);
        Venta venta = Venta.crear(CAJERO, AHORA_PRUEBA);
        venta.agregar(LATTE, 1, Map.of(), ADICIONES_LATTE);
        venta.agregar(fantasma, 1, Map.of(), null);

        assertThrows(DataAccessException.class,
                () -> unidadDeTrabajo.ejecutar(CAFETERIA, () -> { ventas.guardar(venta); return null; }));

        assertTrue(unidadDeTrabajo.ejecutar(CAFETERIA, () -> ventas.buscarPorId(venta.getId())).isEmpty());
        assertEquals(0, unidadDeTrabajo.ejecutar(CAFETERIA, () -> jdbc.queryForObject(
                "SELECT count(*) FROM detalle_venta WHERE id_venta = ?", Integer.class, venta.getId())));
    }

    @Test
    void siLaOperacionFallaDespuesDeGuardarNoQuedaElEgreso() {
        Egreso egreso = Egreso.registrar(ADMIN, INSUMOS, new BigDecimal("1000"), "Se deshace", null, AHORA_PRUEBA);

        assertThrows(IllegalStateException.class, () -> unidadDeTrabajo.ejecutar(CAFETERIA, () -> {
            egresos.guardar(egreso);
            throw new IllegalStateException("falla a propósito después de guardar");
        }));

        assertEquals(0, unidadDeTrabajo.ejecutar(CAFETERIA, () -> jdbc.queryForObject(
                "SELECT count(*) FROM egreso WHERE id = ?", Integer.class, egreso.getId())));
    }

    @Test
    void laCafeteriaActivaSeBorraAlTerminarLaTransaccion() throws Exception {
        // Con una sola conexión: así se ve qué le queda a la conexión cuando vuelve al pool
        try (Connection conexion = dataSource.getConnection()) {
            SingleConnectionDataSource unaConexion = new SingleConnectionDataSource(conexion, true);
            JdbcTemplate jdbcUnaConexion = new JdbcTemplate(unaConexion);
            PgUnidadDeTrabajo uow = new PgUnidadDeTrabajo(
                    new TransactionTemplate(new DataSourceTransactionManager(unaConexion)), jdbcUnaConexion);

            UUID dentro = uow.ejecutar(CAFETERIA,
                    () -> jdbcUnaConexion.queryForObject("SELECT app_cafeteria_actual()", UUID.class));
            UUID despues = jdbcUnaConexion.queryForObject("SELECT app_cafeteria_actual()", UUID.class);

            assertEquals(CAFETERIA, dentro);
            assertNull(despues, "La conexión no debe quedar con la cafetería de la transacción anterior");
        }
    }
}
