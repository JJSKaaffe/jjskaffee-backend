package co.edu.uco.jjskafee.integracion;

import co.edu.uco.jjskafee.compartido.dominio.excepciones.RecursoNoEncontradoException;
import co.edu.uco.jjskafee.compartido.servicios.output.ports.UnidadDeTrabajo;
import co.edu.uco.jjskafee.egresos.servicios.input.dto.request.EgresoRequest;
import co.edu.uco.jjskafee.egresos.servicios.input.ports.RegistrarEgresoUseCase;
import co.edu.uco.jjskafee.ingresos.dominio.IngresoLibre;
import co.edu.uco.jjskafee.ingresos.servicios.input.dto.request.IngresoLibreRequest;
import co.edu.uco.jjskafee.ingresos.servicios.input.dto.request.IngresoRequest;
import co.edu.uco.jjskafee.ingresos.servicios.input.dto.request.ItemRequest;
import co.edu.uco.jjskafee.ingresos.servicios.input.dto.response.IngresoLibreResponse;
import co.edu.uco.jjskafee.ingresos.servicios.input.ports.RegistrarIngresoLibreUseCase;
import co.edu.uco.jjskafee.ingresos.servicios.input.ports.RegistrarIngresoUseCase;
import co.edu.uco.jjskafee.ingresos.servicios.output.ports.IngresoLibreRepository;
import co.edu.uco.jjskafee.movimientos.dominio.enums.TipoMovimiento;
import co.edu.uco.jjskafee.movimientos.servicios.input.dto.request.FiltroMovimientosRequest;
import co.edu.uco.jjskafee.movimientos.servicios.input.dto.response.MovimientoResponse;
import co.edu.uco.jjskafee.movimientos.servicios.input.dto.response.MovimientosResponse;
import co.edu.uco.jjskafee.movimientos.servicios.input.ports.ConsultarMovimientosUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static co.edu.uco.jjskafee.DatosPrueba.*;
import static co.edu.uco.jjskafee.integracion.ConfiguracionDePrueba.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ingresos libres (monto + categoría de ingreso) contra PostgreSQL (db/09 y db/10): HTTP, RLS forzado,
 * v_movimiento y totales. Cada prueba corre en una transacción que se deshace al final.
 */
@PruebaDeIntegracion
@AutoConfigureMockMvc
@Transactional
class IngresosLibresIT {

    @Autowired private MockMvc mvc;
    @Autowired private RegistrarIngresoLibreUseCase registrarIngresoLibre;
    @Autowired private RegistrarIngresoUseCase registrarIngreso;
    @Autowired private RegistrarEgresoUseCase registrarEgreso;
    @Autowired private ConsultarMovimientosUseCase consultarMovimientos;
    @Autowired private IngresoLibreRepository ingresosLibres;
    @Autowired private UnidadDeTrabajo unidadDeTrabajo;
    @Autowired private JdbcTemplate jdbc;

    private static String ingresoLibre(UUID idCategoria, String monto) {
        return """
                {"idCategoria": "%s", "monto": %s, "descripcion": "Cumpleaños de 20 personas", "comprobante": "FAC-77"}"""
                .formatted(idCategoria, monto);
    }

    private static IngresoLibreRequest evento(String monto) {
        return new IngresoLibreRequest(EVENTOS.getId(), new BigDecimal(monto), "Cumpleaños de 20 personas", null);
    }

    private static FiltroMovimientosRequest delDiaDePrueba(TipoMovimiento tipo, UUID idCategoria) {
        return new FiltroMovimientosRequest(DIA_PRUEBA, DIA_PRUEBA, tipo, idCategoria, null, null);
    }

    private static String sqlState(DataAccessException e) {
        return assertInstanceOf(SQLException.class, e.getMostSpecificCause()).getSQLState();
    }

    // ----- POST /ingresos/libres -----

    @Test
    void elAdministradorRegistraUnIngresoLibre() throws Exception {
        mvc.perform(post("/ingresos/libres").headers(sesion(TOKEN_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idCategoria": "%s", "monto": 150000.50, "descripcion": "Cumpleaños de 20 personas",
                                 "comprobante": "FAC-77", "idCafeteria": "%s", "registradoPor": "%s",
                                 "fechaHora": "2020-01-01T00:00:00Z"}"""
                                .formatted(EVENTOS.getId(), CAFETERIA_B, CAJERO.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idIngreso").isNotEmpty())
                .andExpect(jsonPath("$.idCategoria").value(EVENTOS.getId().toString()))
                .andExpect(jsonPath("$.categoria").value("Eventos privados"))
                .andExpect(jsonPath("$.monto").value(150000.50))
                .andExpect(jsonPath("$.comprobante").value("FAC-77"))
                // Cafetería, usuario y fecha los pone el servidor; los del cuerpo se ignoran
                .andExpect(jsonPath("$.registradoPor").value(ADMIN.id().toString()))
                .andExpect(jsonPath("$.fechaHora").value(AHORA_PRUEBA.toString()));
    }

    @Test
    void elCajeroRecibe403() throws Exception {
        mvc.perform(post("/ingresos/libres").headers(sesion(TOKEN_CAJERO))
                        .contentType(MediaType.APPLICATION_JSON).content(ingresoLibre(EVENTOS.getId(), "1000")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Solo el administrador puede registrar ingresos libres"));
        // Laura, con el mismo token con el que es administradora en A, es cajera en B
        mvc.perform(post("/ingresos/libres").headers(sesion(TOKEN_ADMIN, CAFETERIA_B))
                        .contentType(MediaType.APPLICATION_JSON).content(ingresoLibre(EVENTOS.getId(), "1000")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unMontoConTresDecimalesOOnceDigitosEnterosResponde400SinRedondear() throws Exception {
        mvc.perform(post("/ingresos/libres").headers(sesion(TOKEN_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(ingresoLibre(EVENTOS.getId(), "1000.005")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.monto").value("Máximo 10 dígitos enteros y 2 decimales"));
        mvc.perform(post("/ingresos/libres").headers(sesion(TOKEN_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(ingresoLibre(EVENTOS.getId(), "10000000000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.monto").value("Máximo 10 dígitos enteros y 2 decimales"));
        mvc.perform(post("/ingresos/libres").headers(sesion(TOKEN_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(ingresoLibre(EVENTOS.getId(), "9999999999.99")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.monto").value(9999999999.99));
    }

    @Test
    void datosInvalidosResponden400ConLosCampos() throws Exception {
        mvc.perform(post("/ingresos/libres").headers(sesion(TOKEN_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idCategoria": null, "monto": 0, "descripcion": " "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.idCategoria").value("Elige una categoría"))
                .andExpect(jsonPath("$.errores.monto").value("Debe ser mayor que 0"))
                .andExpect(jsonPath("$.errores.descripcion").value("Escribe una descripción"));
    }

    @Test
    void laCategoriaDebeSerDeIngresoActivaYDeLaCafeteria() throws Exception {
        for (UUID idCategoria : List.of(CONVENIOS_ARCHIVADA.getId(), INSUMOS.getId())) {
            mvc.perform(post("/ingresos/libres").headers(sesion(TOKEN_ADMIN))
                            .contentType(MediaType.APPLICATION_JSON).content(ingresoLibre(idCategoria, "1000")))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.detail").value("La categoría debe ser de ingreso y estar activa"));
        }
        // Marta (administradora de B) no ve las categorías de A
        mvc.perform(post("/ingresos/libres").headers(sesion(TOKEN_ADMIN_B))
                        .contentType(MediaType.APPLICATION_JSON).content(ingresoLibre(EVENTOS.getId(), "1000")))
                .andExpect(status().isNotFound());
    }

    @Test
    void laMismaIdempotencyKeyDevuelveElMismoIngresoYConOtroCuerpoResponde422() throws Exception {
        String clave = UUID.randomUUID().toString();

        MvcResult primero = mvc.perform(post("/ingresos/libres").headers(sesion(TOKEN_ADMIN))
                        .header("Idempotency-Key", clave)
                        .contentType(MediaType.APPLICATION_JSON).content(ingresoLibre(EVENTOS.getId(), "50000")))
                .andExpect(status().isCreated())
                .andReturn();
        String idIngreso = primero.getResponse().getContentAsString()
                .replaceAll(".*\"idIngreso\":\"([^\"]+)\".*", "$1");

        mvc.perform(post("/ingresos/libres").headers(sesion(TOKEN_ADMIN))
                        .header("Idempotency-Key", clave)
                        .contentType(MediaType.APPLICATION_JSON).content(ingresoLibre(EVENTOS.getId(), "50000.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idIngreso").value(idIngreso));

        mvc.perform(post("/ingresos/libres").headers(sesion(TOKEN_ADMIN))
                        .header("Idempotency-Key", clave)
                        .contentType(MediaType.APPLICATION_JSON).content(ingresoLibre(EVENTOS.getId(), "60000")))
                .andExpect(status().isUnprocessableContent());

        assertEquals(1, unidadDeTrabajo.ejecutar(CAFETERIA, () -> jdbc.queryForObject(
                "SELECT count(*) FROM ingreso_libre WHERE clave_idempotencia = ?::uuid", Integer.class, clave)));
    }

    // ----- v_movimiento y totales -----

    @Test
    void apareceEnLosMovimientosComoIngresoConSuCategoriaYSumaEnLosTotales() {
        IngresoLibreResponse libre = registrarIngresoLibre.registrar(evento("150000"), ADMIN);
        registrarIngreso.registrar(new IngresoRequest(List.of(new ItemRequest(PANDEBONO.getId(), 1, null))), CAJERO);
        registrarEgreso.registrar(new EgresoRequest(INSUMOS.getId(), new BigDecimal("1000"), "Azúcar", null), ADMIN);

        MovimientosResponse todos = consultarMovimientos.consultar(delDiaDePrueba(null, null), ADMIN);

        assertEquals(3, todos.totalMovimientos());
        assertEquals(0, new BigDecimal("152500").compareTo(todos.totales().ingresos()));   // 150000 + 2500 del pandebono
        assertEquals(0, new BigDecimal("1000").compareTo(todos.totales().egresos()));
        assertEquals(0, new BigDecimal("151500").compareTo(todos.totales().balance()));

        MovimientoResponse movimiento = todos.movimientos().stream()
                .filter(m -> m.idMovimiento().equals(libre.idIngreso())).findFirst().orElseThrow();
        assertEquals(TipoMovimiento.INGRESO.name(), movimiento.tipo());
        assertEquals("Eventos privados", movimiento.categoria());
        assertEquals("Cumpleaños de 20 personas", movimiento.descripcion());
        assertEquals("Laura Gómez", movimiento.registradoPor());
        assertEquals(0, new BigDecimal("150000").compareTo(movimiento.monto()));
    }

    @Test
    void seFiltraPorTipoYPorCategoriaDeIngreso() {
        registrarIngresoLibre.registrar(evento("150000"), ADMIN);
        registrarIngresoLibre.registrar(new IngresoLibreRequest(CAFE_EN_GRANO.getId(), new BigDecimal("80000"),
                "Dos kilos de café", null), ADMIN);
        registrarEgreso.registrar(new EgresoRequest(INSUMOS.getId(), new BigDecimal("1000"), "Azúcar", null), ADMIN);

        MovimientosResponse ingresos = consultarMovimientos.consultar(delDiaDePrueba(TipoMovimiento.INGRESO, null), ADMIN);
        assertEquals(2, ingresos.totalMovimientos());
        assertEquals(0, new BigDecimal("230000").compareTo(ingresos.totales().ingresos()));
        assertEquals(0, BigDecimal.ZERO.compareTo(ingresos.totales().egresos()));

        MovimientosResponse eventos = consultarMovimientos.consultar(delDiaDePrueba(null, EVENTOS.getId()), ADMIN);
        assertEquals(1, eventos.totalMovimientos());
        assertEquals(0, new BigDecimal("150000").compareTo(eventos.totales().ingresos()));
    }

    @Test
    void elCajeroNoVeLosIngresosLibresDelAdministrador() {
        registrarIngresoLibre.registrar(evento("150000"), ADMIN);

        MovimientosResponse delCajero = consultarMovimientos.consultar(delDiaDePrueba(null, null), CAJERO);
        assertEquals(0, delCajero.totalMovimientos());
        assertEquals(0, BigDecimal.ZERO.compareTo(delCajero.totales().ingresos()));
    }

    // ----- RLS y restricciones de la BD -----

    @Test
    void laCafeteriaBNoVeLosIngresosLibresDeA() {
        IngresoLibreResponse libre = registrarIngresoLibre.registrar(evento("150000"), ADMIN);

        assertTrue(unidadDeTrabajo.ejecutar(CAFETERIA, () -> ingresosLibres.buscarPorId(libre.idIngreso())).isPresent());
        assertTrue(unidadDeTrabajo.ejecutar(CAFETERIA_B, () -> ingresosLibres.buscarPorId(libre.idIngreso())).isEmpty());
        assertEquals(0, consultarMovimientos.consultar(delDiaDePrueba(null, null), ADMIN_B).totalMovimientos());
        // Con la identidad fijada (sin cafetería) tampoco se ve
        assertEquals(0, unidadDeTrabajo.ejecutarConIdentidad(UID_ADMIN,
                () -> jdbc.queryForObject("SELECT count(*) FROM ingreso_libre", Integer.class)));
        // Ni la categoría de A existe para B
        assertThrows(RecursoNoEncontradoException.class, () -> registrarIngresoLibre.registrar(evento("1000"), ADMIN_B));
    }

    @Test
    void laBaseRechazaGuardarUnIngresoDeAMientrasLaCafeteriaActivaEsB() {
        IngresoLibre deA = IngresoLibre.registrar(ADMIN, EVENTOS, new BigDecimal("1000"), "Intento cruzado", null, AHORA_PRUEBA);

        DataAccessException e = assertThrows(DataAccessException.class,
                () -> unidadDeTrabajo.ejecutar(CAFETERIA_B, () -> ingresosLibres.guardar(deA)));
        assertEquals("42501", sqlState(e));   // la nueva fila viola la política de seguridad de filas
    }

    @Test
    void elRepositorioGuardaYLeeElIngresoConSuClaveYSuHuella() {
        UUID clave = UUID.randomUUID();
        IngresoLibre ingreso = IngresoLibre.registrar(ADMIN, EVENTOS, new BigDecimal("1234.50"), "Evento",
                "FAC-1", clave, "a".repeat(64), AHORA_PRUEBA);

        assertTrue(unidadDeTrabajo.ejecutar(CAFETERIA, () -> ingresosLibres.guardar(ingreso)));
        IngresoLibre leido = unidadDeTrabajo.ejecutar(CAFETERIA, () -> ingresosLibres.buscarPorClaveIdempotencia(clave))
                .orElseThrow();

        assertEquals(ingreso.getId(), leido.getId());
        assertEquals(CAFETERIA, leido.getIdCafeteria());
        assertEquals(0, new BigDecimal("1234.50").compareTo(leido.getMonto()));
        assertEquals("Eventos privados", leido.getCategoria().getNombre());
        assertEquals(ADMIN.id(), leido.getRegistradoPor());
        assertEquals(AHORA_PRUEBA, leido.getFechaHora());
        assertEquals("a".repeat(64), leido.getHashSolicitud());
        assertFalse(leido.isAnulado());
    }

    @Test
    void laUniqueRealNoGuardaDosIngresosConLaMismaClaveEnLaMismaCafeteria() {
        UUID clave = UUID.randomUUID();
        IngresoLibre primero = IngresoLibre.registrar(ADMIN, EVENTOS, new BigDecimal("1000"), "Evento", null,
                clave, "a".repeat(64), AHORA_PRUEBA);
        IngresoLibre segundo = IngresoLibre.registrar(ADMIN, EVENTOS, new BigDecimal("1000"), "Evento", null,
                clave, "a".repeat(64), AHORA_PRUEBA);

        assertTrue(unidadDeTrabajo.ejecutar(CAFETERIA, () -> ingresosLibres.guardar(primero)));
        assertFalse(unidadDeTrabajo.ejecutar(CAFETERIA, () -> ingresosLibres.guardar(segundo)));
        assertEquals(primero.getId(), unidadDeTrabajo.ejecutar(CAFETERIA,
                () -> ingresosLibres.buscarPorClaveIdempotencia(clave)).orElseThrow().getId());
    }

    @Test
    void laBaseRechazaUnMontoDeCero() {
        // Aunque el dominio fallara, el CHECK (monto > 0) de la tabla no deja guardar
        DataAccessException e = assertThrows(DataAccessException.class, () -> unidadDeTrabajo.ejecutar(CAFETERIA,
                () -> jdbc.update("""
                        INSERT INTO ingreso_libre (id, id_cafeteria, id_categoria, monto, descripcion, registrado_por, fecha_hora)
                        VALUES (?, ?, ?, 0, 'x', ?, now())""", UUID.randomUUID(), CAFETERIA, EVENTOS.getId(), ADMIN.id())));
        assertEquals("23514", sqlState(e));   // check_violation
    }

    @Test
    void laAppSoloPuedeLeerEInsertarIngresosLibres() {
        assertEquals(List.of(true, true, false, false), jdbc.queryForList("""
                SELECT has_table_privilege(current_user, 'ingreso_libre', p)
                FROM unnest(ARRAY['SELECT', 'INSERT', 'UPDATE', 'DELETE']) WITH ORDINALITY AS t(p, orden)
                ORDER BY orden""", Boolean.class));
    }

    @Test
    void laTablaTieneRlsForzado() {
        assertEquals(List.of(true, true), jdbc.queryForObject(
                "SELECT relrowsecurity, relforcerowsecurity FROM pg_class WHERE relname = 'ingreso_libre'",
                (rs, n) -> List.of(rs.getBoolean(1), rs.getBoolean(2))));
    }
}
