package co.edu.uco.jjskafee.integracion;

import co.edu.uco.jjskafee.dominio.Venta;
import co.edu.uco.jjskafee.dominio.excepciones.ReglaNegocioException;
import co.edu.uco.jjskafee.infraestructura.output.repositorios.estructurados.PgCafeteriaRepository;
import co.edu.uco.jjskafee.servicios.input.dto.request.IngresoRequest;
import co.edu.uco.jjskafee.servicios.input.dto.request.ItemRequest;
import co.edu.uco.jjskafee.servicios.input.dto.response.IngresoResponse;
import co.edu.uco.jjskafee.servicios.input.ports.RegistrarIngresoUseCase;
import co.edu.uco.jjskafee.servicios.output.ports.UnidadDeTrabajo;
import co.edu.uco.jjskafee.servicios.output.ports.VentaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static co.edu.uco.jjskafee.DatosPrueba.*;
import static co.edu.uco.jjskafee.integracion.ConfiguracionDePrueba.AHORA_PRUEBA;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Validaciones de registrar ingreso contra PostgreSQL: cafetería activa (db/05 y db/06),
 * idempotencia con la UNIQUE (id_cafeteria, clave_idempotencia) y fechas timestamptz en UTC.
 * Todo se deshace al final.
 */
@PruebaDeIntegracion
@Transactional
class RegistrarIngresoValidacionesIT {

    @Autowired private RegistrarIngresoUseCase registrarIngreso;
    @Autowired private UnidadDeTrabajo unidadDeTrabajo;
    @Autowired private VentaRepository ventas;
    @Autowired private JdbcTemplate jdbc;

    private static IngresoRequest pandebonos(int cantidad) {
        return new IngresoRequest(List.of(new ItemRequest(PANDEBONO.getId(), cantidad, null)));
    }

    private int ventasConClave(UUID idCafeteria, UUID clave) {
        return unidadDeTrabajo.ejecutar(idCafeteria, () -> jdbc.queryForObject(
                "SELECT count(*) FROM venta WHERE clave_idempotencia = ?", Integer.class, clave));
    }

    // ----- Cafetería activa -----

    @Test
    void unaCafeteriaInactivaNoRegistraIngresosYNoQuedaNada() {
        IngresoRequest tinto = new IngresoRequest(List.of(new ItemRequest(TINTO_INACTIVA.getId(), 1, null)));

        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> registrarIngreso.registrar(tinto, CAJERO_INACTIVA));
        assertEquals("La cafetería no está activa; no se pueden registrar ingresos", e.getMessage());
        assertEquals(0, unidadDeTrabajo.ejecutar(CAFETERIA_INACTIVA,
                () -> jdbc.queryForObject("SELECT count(*) FROM venta", Integer.class)));
    }

    @Test
    void laAppSoloVeElEstadoDeSuPropiaCafeteria() {
        PgCafeteriaRepository cafeterias = new PgCafeteriaRepository(jdbc);

        assertTrue(unidadDeTrabajo.ejecutar(CAFETERIA, () -> cafeterias.estaActiva(CAFETERIA)));
        assertFalse(unidadDeTrabajo.ejecutar(CAFETERIA_INACTIVA, () -> cafeterias.estaActiva(CAFETERIA_INACTIVA)));
        // B está activa, pero desde A no se ve (RLS): cuenta como no activa
        assertFalse(unidadDeTrabajo.ejecutar(CAFETERIA, () -> cafeterias.estaActiva(CAFETERIA_B)));
        assertTrue(unidadDeTrabajo.ejecutar(CAFETERIA_B, () -> cafeterias.estaActiva(CAFETERIA_B)));
    }

    // Un error SQL aborta la transacción de la prueba: por eso cada permiso va en su propia prueba

    @Test
    void laAppSoloPuedeLeerIdYActivoDeLaCafeteria() {
        DataAccessException e = assertThrows(DataAccessException.class, () -> unidadDeTrabajo.ejecutar(CAFETERIA,
                () -> jdbc.queryForObject("SELECT nombre FROM cafeteria WHERE id = ?", String.class, CAFETERIA)));
        assertEquals("42501", sqlState(e));   // insufficient_privilege
    }

    @Test
    void laAppNoPuedeReactivarUnaCafeteria() {
        DataAccessException e = assertThrows(DataAccessException.class, () -> unidadDeTrabajo.ejecutar(CAFETERIA_INACTIVA,
                () -> jdbc.update("UPDATE cafeteria SET activo = true WHERE id = ?", CAFETERIA_INACTIVA)));
        assertEquals("42501", sqlState(e));
    }

    // ----- Idempotencia -----

    @Test
    void laMismaClaveDosVecesGuardaUnaSolaVenta() {
        UUID clave = UUID.randomUUID();

        IngresoResponse primera = registrarIngreso.registrar(pandebonos(2), clave, CAJERO);
        IngresoResponse segunda = registrarIngreso.registrar(pandebonos(2), clave, CAJERO);

        assertEquals(primera.idVenta(), segunda.idVenta());
        assertEquals(0, primera.total().compareTo(segunda.total()));
        assertEquals(1, ventasConClave(CAFETERIA, clave));
        String hash = unidadDeTrabajo.ejecutar(CAFETERIA, () -> jdbc.queryForObject(
                "SELECT hash_solicitud FROM venta WHERE id = ?", String.class, primera.idVenta()));
        assertEquals(64, hash.length());
    }

    @Test
    void laMismaClaveConOtroCuerpoSeRechazaYNoCreaOtraVenta() {
        UUID clave = UUID.randomUUID();
        registrarIngreso.registrar(pandebonos(1), clave, CAJERO);

        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> registrarIngreso.registrar(pandebonos(3), clave, CAJERO));
        assertTrue(e.getMessage().contains("Idempotency-Key"), e.getMessage());
        assertEquals(1, ventasConClave(CAFETERIA, clave));
    }

    @Test
    void laMismaClaveEnOtraCafeteriaEsOtraVenta() {
        UUID clave = UUID.randomUUID();
        IngresoRequest capuchino = new IngresoRequest(List.of(new ItemRequest(CAPUCHINO_B.getId(), 1, null)));

        IngresoResponse deA = registrarIngreso.registrar(pandebonos(1), clave, CAJERO);
        IngresoResponse deB = registrarIngreso.registrar(capuchino, clave, CAJERO_B);

        assertNotEquals(deA.idVenta(), deB.idVenta());
        assertEquals(1, ventasConClave(CAFETERIA, clave));
        assertEquals(1, ventasConClave(CAFETERIA_B, clave));
    }

    @Test
    void laRestriccionUniqueNoDejaGuardarDosVentasConLaMismaClave() {
        // Lo que pasa cuando dos peticiones iguales llegan a la vez: la segunda no se guarda (ON CONFLICT)
        UUID clave = UUID.randomUUID();
        Venta primera = Venta.crear(CAJERO, clave, "a".repeat(64), AHORA_PRUEBA);
        primera.agregar(PANDEBONO, 1, Map.of(), Set.of());
        Venta segunda = Venta.crear(CAJERO, clave, "a".repeat(64), AHORA_PRUEBA);
        segunda.agregar(PANDEBONO, 1, Map.of(), Set.of());

        assertTrue(unidadDeTrabajo.ejecutar(CAFETERIA, () -> ventas.guardar(primera)));
        assertFalse(unidadDeTrabajo.ejecutar(CAFETERIA, () -> ventas.guardar(segunda)));

        assertEquals(primera.getId(), unidadDeTrabajo.ejecutar(CAFETERIA,
                () -> ventas.buscarPorClaveIdempotencia(clave)).orElseThrow().getId());
        assertEquals(0, unidadDeTrabajo.ejecutar(CAFETERIA, () -> jdbc.queryForObject(
                "SELECT count(*) FROM detalle_venta WHERE id_venta = ?", Integer.class, segunda.getId())));
    }

    // ----- Fechas en timestamptz (UTC) -----

    @Test
    void todasLasFechasSonTimestamptz() {
        // pg_attribute y no information_schema: este último oculta las columnas sin permiso (cafeteria.creada_en)
        List<Map<String, Object>> columnas = jdbc.queryForList("""
                SELECT c.relname AS tabla, a.attname AS columna, format_type(a.atttypid, a.atttypmod) AS tipo
                FROM pg_attribute a
                JOIN pg_class c ON c.oid = a.attrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relkind = 'r' AND a.attnum > 0 AND NOT a.attisdropped
                  AND a.atttypid IN ('timestamp'::regtype, 'timestamptz'::regtype, 'date'::regtype,
                                     'time'::regtype, 'timetz'::regtype)""");

        List<String> nombres = columnas.stream().map(c -> c.get("tabla") + "." + c.get("columna")).toList();
        assertTrue(nombres.containsAll(List.of("cafeteria.creada_en", "venta.fecha_hora", "egreso.fecha_hora")),
                "Faltan columnas de fecha: " + nombres);
        columnas.forEach(c -> assertEquals("timestamp with time zone", c.get("tipo"),
                c.get("tabla") + "." + c.get("columna") + " debe ser timestamptz"));
    }

    @Test
    void laFechaSeGuardaComoInstanteUtcSinImportarLaZonaDeLaSesion() {
        // Aunque la sesión de PostgreSQL esté en hora de Colombia, el instante guardado y leído es el mismo
        jdbc.execute("SET LOCAL TIME ZONE 'America/Bogota'");

        IngresoResponse r = registrarIngreso.registrar(pandebonos(1), CAJERO);

        Venta leida = unidadDeTrabajo.ejecutar(CAFETERIA, () -> ventas.buscarPorId(r.idVenta())).orElseThrow();
        assertEquals(AHORA_PRUEBA, leida.getFechaHora());
        String enUtc = unidadDeTrabajo.ejecutar(CAFETERIA, () -> jdbc.queryForObject(
                "SELECT to_char(fecha_hora AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"') FROM venta WHERE id = ?",
                String.class, r.idVenta()));
        assertEquals(AHORA_PRUEBA.toString(), enUtc);
    }

    private static String sqlState(DataAccessException e) {
        return assertInstanceOf(SQLException.class, e.getMostSpecificCause()).getSQLState();
    }
}
