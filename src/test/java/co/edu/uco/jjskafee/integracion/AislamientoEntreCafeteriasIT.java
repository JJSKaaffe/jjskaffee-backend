package co.edu.uco.jjskafee.integracion;

import co.edu.uco.jjskafee.dominio.Egreso;
import co.edu.uco.jjskafee.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.dominio.excepciones.RecursoNoEncontradoException;
import co.edu.uco.jjskafee.infraestructura.output.repositorios.estructurados.PgUsuarioRepository;
import co.edu.uco.jjskafee.servicios.input.dto.request.EgresoRequest;
import co.edu.uco.jjskafee.servicios.input.dto.request.FiltroMovimientosRequest;
import co.edu.uco.jjskafee.servicios.input.dto.request.IngresoRequest;
import co.edu.uco.jjskafee.servicios.input.dto.request.ItemRequest;
import co.edu.uco.jjskafee.servicios.input.dto.response.IngresoResponse;
import co.edu.uco.jjskafee.servicios.input.dto.response.MovimientosResponse;
import co.edu.uco.jjskafee.servicios.input.ports.ConsultarMovimientosUseCase;
import co.edu.uco.jjskafee.servicios.input.ports.RegistrarEgresoUseCase;
import co.edu.uco.jjskafee.servicios.input.ports.RegistrarIngresoUseCase;
import co.edu.uco.jjskafee.servicios.output.ports.CatalogoRepository;
import co.edu.uco.jjskafee.servicios.output.ports.EgresoRepository;
import co.edu.uco.jjskafee.servicios.output.ports.UnidadDeTrabajo;
import co.edu.uco.jjskafee.servicios.output.ports.VentaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static co.edu.uco.jjskafee.DatosPrueba.*;
import static co.edu.uco.jjskafee.integracion.ConfiguracionDePrueba.AHORA_PRUEBA;
import static co.edu.uco.jjskafee.integracion.ConfiguracionDePrueba.DIA_PRUEBA;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Multi-tenant (ADR-0001, ADR-0012, SPIKE-03): la cafetería A y la cafetería B nunca ven
 * ni modifican los datos de la otra. Lo garantiza PostgreSQL con RLS forzado, no el código Java.
 * Cada prueba corre en una transacción que se deshace al final: no deja datos en la BD.
 */
@PruebaDeIntegracion
@Transactional
class AislamientoEntreCafeteriasIT {

    @Autowired private RegistrarIngresoUseCase registrarIngreso;
    @Autowired private RegistrarEgresoUseCase registrarEgreso;
    @Autowired private ConsultarMovimientosUseCase consultarMovimientos;
    @Autowired private UnidadDeTrabajo unidadDeTrabajo;
    @Autowired private VentaRepository ventas;
    @Autowired private EgresoRepository egresos;
    @Autowired private CatalogoRepository catalogo;
    @Autowired private JdbcTemplate jdbc;

    private static FiltroMovimientosRequest delDiaDePrueba() {
        return new FiltroMovimientosRequest(DIA_PRUEBA, DIA_PRUEBA, null, null, null, null);
    }

    private MovimientosResponse movimientosDe(UsuarioAutenticado usuario) {
        return consultarMovimientos.consultar(delDiaDePrueba(), usuario);
    }

    @Test
    void laCafeteriaBNoVeLosIngresosNiEgresosDeA() {
        IngresoResponse ventaA = registrarIngreso.registrar(
                new IngresoRequest(List.of(new ItemRequest(LATTE.getId(), 1, null))), CAJERO);
        registrarEgreso.registrar(
                new EgresoRequest(INSUMOS.getId(), new BigDecimal("38000"), "Compra de leche", null), ADMIN);

        MovimientosResponse vistosPorA = movimientosDe(ADMIN);
        MovimientosResponse vistosPorB = movimientosDe(ADMIN_B);

        assertEquals(2, vistosPorA.totalMovimientos());
        assertTrue(vistosPorA.movimientos().stream().anyMatch(m -> m.idMovimiento().equals(ventaA.idVenta())));

        assertEquals(0, vistosPorB.totalMovimientos());
        assertTrue(vistosPorB.movimientos().isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(vistosPorB.totales().ingresos()));
        assertEquals(0, BigDecimal.ZERO.compareTo(vistosPorB.totales().egresos()));
    }

    @Test
    void cadaCafeteriaSoloSumaLoSuyo() {
        registrarEgreso.registrar(new EgresoRequest(INSUMOS.getId(), new BigDecimal("10000"), "Azúcar", null), ADMIN);
        registrarEgreso.registrar(new EgresoRequest(INSUMOS_B.getId(), new BigDecimal("777"), "Servilletas", null), ADMIN_B);

        assertEquals(0, new BigDecimal("10000").compareTo(movimientosDe(ADMIN).totales().egresos()));
        assertEquals(0, new BigDecimal("777").compareTo(movimientosDe(ADMIN_B).totales().egresos()));
    }

    @Test
    void laCafeteriaBNoEncuentraLaVentaDeANiPuedeDeshacerla() {
        IngresoResponse ventaA = registrarIngreso.registrar(
                new IngresoRequest(List.of(new ItemRequest(PANDEBONO.getId(), 2, null))), CAJERO);

        assertTrue(unidadDeTrabajo.ejecutar(CAFETERIA_B, () -> ventas.buscarPorId(ventaA.idVenta())).isEmpty());
        assertThrows(RecursoNoEncontradoException.class, () -> registrarIngreso.deshacer(ventaA.idVenta(), CAJERO_B));
        // Para A sigue intacta
        assertTrue(unidadDeTrabajo.ejecutar(CAFETERIA, () -> ventas.buscarPorId(ventaA.idVenta())).isPresent());
    }

    @Test
    void elCatalogoDeUnaCafeteriaNoExisteParaLaOtra() {
        assertTrue(unidadDeTrabajo.ejecutar(CAFETERIA_B, () -> catalogo.buscarProducto(LATTE.getId())).isEmpty());
        assertTrue(unidadDeTrabajo.ejecutar(CAFETERIA_B, () -> catalogo.buscarCategoria(INSUMOS.getId())).isEmpty());
        assertTrue(unidadDeTrabajo.ejecutar(CAFETERIA, () -> catalogo.buscarProducto(CAPUCHINO_B.getId())).isEmpty());
        assertTrue(unidadDeTrabajo.ejecutar(CAFETERIA_B, () -> catalogo.adicionesPermitidas(LATTE.getId())).isEmpty());
    }

    @Test
    void nadieVendeProductosNiUsaCategoriasDeOtraCafeteria() {
        assertThrows(RecursoNoEncontradoException.class, () -> registrarIngreso.registrar(
                new IngresoRequest(List.of(new ItemRequest(CAPUCHINO_B.getId(), 1, null))), CAJERO));
        assertThrows(RecursoNoEncontradoException.class, () -> registrarEgreso.registrar(
                new EgresoRequest(INSUMOS_B.getId(), new BigDecimal("5000"), "x", null), ADMIN));
    }

    @Test
    void laBaseRechazaEscribirDatosDeAMientrasLaCafeteriaActivaEsB() {
        // Aunque el código Java tuviera un error, PostgreSQL no deja guardar en otra cafetería
        Egreso egresoDeA = Egreso.registrar(ADMIN, INSUMOS, new BigDecimal("1000"), "Intento cruzado", null, AHORA_PRUEBA);

        DataAccessException e = assertThrows(DataAccessException.class,
                () -> unidadDeTrabajo.ejecutar(CAFETERIA_B, () -> { egresos.guardar(egresoDeA); return null; }));
        // 42501 = insufficient_privilege: "la nueva fila viola la política de seguridad de filas" (no depende del idioma)
        SQLException causa = assertInstanceOf(SQLException.class, e.getMostSpecificCause());
        assertEquals("42501", causa.getSQLState(), causa.getMessage());
    }

    @Test
    void sinCafeteriaActivaNoSeVeNingunDato() {
        // Consulta directa, sin pasar por UnidadDeTrabajo: no hay cafetería fijada en la transacción
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM producto", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM categoria", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM usuarios", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM v_movimiento", Integer.class));
    }

    @Test
    void unUsuarioSoloSeEncuentraDentroDeSuCafeteria() {
        PgUsuarioRepository usuarios = new PgUsuarioRepository(jdbc);

        assertEquals(ADMIN, unidadDeTrabajo.ejecutar(CAFETERIA, () -> usuarios.buscarActivoPorFirebaseUid(UID_ADMIN)).orElseThrow());
        // Un token que diga ser de la cafetería B no sirve para un usuario de A
        assertTrue(unidadDeTrabajo.ejecutar(CAFETERIA_B, () -> usuarios.buscarActivoPorFirebaseUid(UID_ADMIN)).isEmpty());
    }

    // ----- La configuración de la BD que hace posible lo anterior -----

    @Test
    void laAplicacionNoSeConectaComoSuperusuarioNiPuedeSaltarseRls() {
        Map<String, Object> rol = jdbc.queryForMap(
                "SELECT rolname, rolsuper, rolbypassrls FROM pg_roles WHERE rolname = current_user");

        assertEquals(false, rol.get("rolsuper"), "La app no debe usar un superusuario: " + rol.get("rolname"));
        assertEquals(false, rol.get("rolbypassrls"), "La app no debe tener BYPASSRLS: " + rol.get("rolname"));
    }

    @Test
    void todaTablaConIdCafeteriaTieneRlsActivadoYForzado() {
        List<Map<String, Object>> tablas = jdbc.queryForList("""
                SELECT c.relname, c.relrowsecurity, c.relforcerowsecurity
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relkind = 'r'
                  AND (c.relname = 'cafeteria' OR EXISTS (
                        SELECT 1 FROM pg_attribute a
                        WHERE a.attrelid = c.oid AND a.attname = 'id_cafeteria' AND NOT a.attisdropped))""");

        assertTrue(tablas.size() >= 8, "Se esperaban al menos 8 tablas multi-tenant: " + tablas);
        tablas.forEach(t -> {
            assertEquals(true, t.get("relrowsecurity"), "RLS no está activado en " + t.get("relname"));
            assertEquals(true, t.get("relforcerowsecurity"), "RLS no está forzado en " + t.get("relname"));
        });
    }

    @Test
    void laVistaDeMovimientosRespetaElRlsDeQuienConsulta() {
        String opciones = jdbc.queryForObject(
                "SELECT array_to_string(reloptions, ',') FROM pg_class WHERE relname = 'v_movimiento'", String.class);
        assertNotNull(opciones);
        assertTrue(opciones.contains("security_invoker=true"),
                "Sin security_invoker la vista correría como su dueño y se saltaría RLS: " + opciones);
    }
}
