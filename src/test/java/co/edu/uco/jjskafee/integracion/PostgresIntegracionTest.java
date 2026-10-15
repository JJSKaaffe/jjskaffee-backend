package co.edu.uco.jjskafee.integracion;

import co.edu.uco.jjskafee.catalogo.aplicacion.ServicioCatalogo;
import co.edu.uco.jjskafee.catalogo.infraestructura.persistencia.PgRepositorioCatalogo;
import co.edu.uco.jjskafee.compartido.aplicacion.puertos.UnidadDeTrabajo;
import co.edu.uco.jjskafee.compartido.infraestructura.persistencia.PgRepositorioUsuarios;
import co.edu.uco.jjskafee.compartido.infraestructura.persistencia.PgUnidadDeTrabajo;
import co.edu.uco.jjskafee.egresos.aplicacion.RegistrarEgreso;
import co.edu.uco.jjskafee.egresos.aplicacion.comandos.RegistrarEgresoComando;
import co.edu.uco.jjskafee.egresos.dominio.Egreso;
import co.edu.uco.jjskafee.egresos.infraestructura.persistencia.PgRepositorioEgresos;
import co.edu.uco.jjskafee.egresos.infraestructura.salida.AdaptadorCategoriasEgreso;
import co.edu.uco.jjskafee.ingresos.aplicacion.RegistrarIngreso;
import co.edu.uco.jjskafee.ingresos.aplicacion.comandos.RegistrarVentaComando;
import co.edu.uco.jjskafee.ingresos.dominio.EstadoVenta;
import co.edu.uco.jjskafee.ingresos.dominio.Venta;
import co.edu.uco.jjskafee.ingresos.infraestructura.persistencia.PgRepositorioVentas;
import co.edu.uco.jjskafee.ingresos.infraestructura.salida.AdaptadorCatalogoProductos;
import co.edu.uco.jjskafee.movimientos.aplicacion.ConsultarMovimientos;
import co.edu.uco.jjskafee.movimientos.dominio.FiltroMovimientos;
import co.edu.uco.jjskafee.movimientos.dominio.PaginaMovimientos;
import co.edu.uco.jjskafee.movimientos.infraestructura.persistencia.PgRepositorioMovimientos;
import co.edu.uco.jjskafee.soporte.Dobles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static co.edu.uco.jjskafee.soporte.Dobles.ADMIN;
import static co.edu.uco.jjskafee.soporte.Dobles.ADMIN_VECINO;
import static co.edu.uco.jjskafee.soporte.Dobles.CAFE_DEMO;
import static co.edu.uco.jjskafee.soporte.Dobles.CAFE_VECINO;
import static co.edu.uco.jjskafee.soporte.Dobles.CAJERO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas contra PostgreSQL real con el usuario limitado app_jjskaffee (SPIKE-03).
 * Requieren la base cargada con db/jjskaffee_completo.sql y estas variables:
 *   JJSKAFFEE_PRUEBAS_DB_URL, JJSKAFFEE_PRUEBAS_DB_USUARIO, JJSKAFFEE_PRUEBAS_DB_CONTRASENA
 * Sin esas variables se omiten (no fallan). En GitHub Actions corren con un servicio postgres:17.
 *
 * Cada prueba corre dentro de una transacción que al final se DESHACE: tu base local no se ensucia
 * (el usuario de la app no tiene DELETE, así que no habría otra forma de limpiar).
 */
@EnabledIfEnvironmentVariable(named = "JJSKAFFEE_PRUEBAS_DB_URL", matches = ".+")
class PostgresIntegracionTest {

    private static final UUID CAPUCHINO = UUID.fromString("b0000000-0000-0000-0000-000000000003");
    private static final UUID LECHE_ALMENDRA = UUID.fromString("b0000000-0000-0000-0000-000000000011");
    private static final UUID INSUMOS_DEMO = UUID.fromString("c0000000-0000-0000-0000-000000000011");
    private static final UUID INSUMOS_VECINO = UUID.fromString("c0000000-0000-0000-0000-000000000021");

    private JdbcTemplate jdbc;
    private TransactionTemplate transaccionExterna;
    private UnidadDeTrabajo unidad;
    private ServicioCatalogo catalogo;

    @BeforeEach
    void conectar() {
        DriverManagerDataSource fuente = new DriverManagerDataSource(
                System.getenv("JJSKAFFEE_PRUEBAS_DB_URL"),
                System.getenv("JJSKAFFEE_PRUEBAS_DB_USUARIO"),
                System.getenv("JJSKAFFEE_PRUEBAS_DB_CONTRASENA"));
        DataSourceTransactionManager transacciones = new DataSourceTransactionManager(fuente);
        jdbc = new JdbcTemplate(fuente);
        transaccionExterna = new TransactionTemplate(transacciones);
        unidad = new PgUnidadDeTrabajo(new TransactionTemplate(transacciones), jdbc);
        catalogo = new ServicioCatalogo(new PgRepositorioCatalogo(jdbc), unidad);
    }

    /** Corre la prueba en una transacción que siempre se revierte. */
    private void enTransaccionQueSeRevierte(Runnable prueba) {
        transaccionExterna.executeWithoutResult(estado -> {
            estado.setRollbackOnly();
            prueba.run();
        });
    }

    private RegistrarIngreso registrarIngreso(Instant ahora) {
        return new RegistrarIngreso(new AdaptadorCatalogoProductos(catalogo), new PgRepositorioVentas(jdbc), unidad,
                new Dobles.MensajesPorClave(), Dobles.relojFijo(ahora));
    }

    private ConsultarMovimientos consultarMovimientos() {
        return new ConsultarMovimientos(new PgRepositorioMovimientos(jdbc), unidad, new Dobles.MensajesPorClave());
    }

    private static FiltroMovimientos todo() {
        return FiltroMovimientos.crear(null, null, null, null, 0, FiltroMovimientos.TAMANO_MAXIMO);
    }

    // ---------------- Aislamiento entre cafeterías (EXP05-01: 0 % de fuga) ----------------

    @Test
    void laCafeteriaANoVeDatosDeLaCafeteriaB() {
        enTransaccionQueSeRevierte(() -> {
            PaginaMovimientos demo = consultarMovimientos().consultar(todo(), ADMIN);
            assertTrue(demo.movimientos().stream().noneMatch(m -> m.descripcion().contains("Vecino")));
            assertTrue(catalogo.buscarCategoria(CAFE_DEMO, INSUMOS_VECINO).isEmpty());
            assertTrue(new PgRepositorioUsuarios(jdbc).buscarActivoPorUid(CAFE_DEMO, "firebase-admin-vecino").isEmpty());
        });
        enTransaccionQueSeRevierte(() -> {
            PaginaMovimientos vecino = consultarMovimientos().consultar(todo(), ADMIN_VECINO);
            assertEquals(1, vecino.totalElementos());
        });
    }

    @Test
    void sinCafeteriaActivaNoSeVeNada() {
        enTransaccionQueSeRevierte(() ->
                assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM egreso", Integer.class)));
    }

    @Test
    void unaTransaccionNoPuedeMezclarDosCafeterias() {
        enTransaccionQueSeRevierte(() -> {
            unidad.ejecutar(CAFE_DEMO, () -> null);
            assertThrows(IllegalStateException.class, () -> unidad.ejecutar(CAFE_VECINO, () -> null));
        });
    }

    // ---------------- Ingresos ----------------

    @Test
    void registrarUnaVentaConAdicionYDeshacerla() {
        enTransaccionQueSeRevierte(() -> {
            Instant ahora = Instant.now();
            BigDecimal antes = consultarMovimientos().consultar(todo(), ADMIN).totales().ingresos();

            Venta venta = registrarIngreso(ahora).registrar(new RegistrarVentaComando(List.of(
                    new RegistrarVentaComando.Linea(CAPUCHINO, 1,
                            List.of(new RegistrarVentaComando.Adicion(LECHE_ALMENDRA, 1))))), CAJERO);

            // 7000 + 1500 calculado en el servidor y la columna generada subtotal cuadra con el total
            assertEquals(0, new BigDecimal("8500").compareTo(venta.total()));
            BigDecimal sumaDetalle = unidad.ejecutar(CAFE_DEMO, () -> jdbc.queryForObject(
                    "SELECT sum(subtotal) FROM detalle_venta WHERE id_venta = ?", BigDecimal.class, venta.getId()));
            assertEquals(0, new BigDecimal("8500").compareTo(sumaDetalle));
            BigDecimal despues = consultarMovimientos().consultar(todo(), ADMIN).totales().ingresos();
            assertEquals(0, antes.add(new BigDecimal("8500")).compareTo(despues));

            Venta anulada = registrarIngreso(ahora.plusSeconds(10)).deshacer(venta.getId(), CAJERO);
            assertEquals(EstadoVenta.ANULADA, anulada.getEstado());
            assertEquals(0, antes.compareTo(consultarMovimientos().consultar(todo(), ADMIN).totales().ingresos()));
        });
    }

    @Test
    void elCajeroSoloVeLoQueElRegistro() {
        enTransaccionQueSeRevierte(() -> {
            PaginaMovimientos delCajero = consultarMovimientos().consultar(todo(), CAJERO);
            assertFalse(delCajero.movimientos().isEmpty());
            assertTrue(delCajero.movimientos().stream().allMatch(m -> "Andrés Ruiz".equals(m.registradoPor())));
        });
    }

    // ---------------- Egresos ----------------

    @Test
    void registrarUnEgresoQuedaEnLosMovimientos() {
        enTransaccionQueSeRevierte(() -> {
            RegistrarEgreso registrarEgreso = new RegistrarEgreso(new AdaptadorCategoriasEgreso(catalogo),
                    new PgRepositorioEgresos(jdbc), unidad, Dobles.relojFijo(Instant.now()));

            Egreso egreso = registrarEgreso.registrar(new RegistrarEgresoComando(INSUMOS_DEMO,
                    new BigDecimal("30000"), "Prueba: azúcar y vasos", "PRB-1"), ADMIN);

            PaginaMovimientos pagina = consultarMovimientos().consultar(todo(), ADMIN);
            assertTrue(pagina.movimientos().stream().anyMatch(m -> m.id().equals(egreso.getId())
                    && "Insumos".equals(m.categoria())));
        });
    }
}
