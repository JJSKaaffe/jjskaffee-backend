package co.edu.uco.jjskafee.integracion;

import co.edu.uco.jjskafee.acceso.dominio.CafeteriaDelUsuario;
import co.edu.uco.jjskafee.acceso.infraestructura.output.repositorios.estructurados.PgUsuarioRepository;
import co.edu.uco.jjskafee.compartido.dominio.enums.Rol;
import co.edu.uco.jjskafee.compartido.servicios.output.ports.UnidadDeTrabajo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static co.edu.uco.jjskafee.DatosPrueba.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Multi-cafetería por persona con RLS (db/07, ADR-0016): con app.current_uid solo se ven las membresías
 * de esa persona y sus cafeterías; nunca datos de negocio. Las dos variables nunca quedan fijadas a la vez.
 * Cada prueba corre en una transacción que se deshace al final.
 */
@PruebaDeIntegracion
@Transactional
class MultiCafeteriaIT {

    @Autowired private UnidadDeTrabajo unidadDeTrabajo;
    @Autowired private JdbcTemplate jdbc;

    private Set<UUID> cafeteriasVisiblesPara(String uid) {
        return Set.copyOf(unidadDeTrabajo.ejecutarConIdentidad(uid,
                () -> jdbc.queryForList("SELECT id FROM cafeteria", UUID.class)));
    }

    @Test
    void conLaIdentidadSoloSeVenLasMembresiasDeEsaPersona() {
        List<String> uids = unidadDeTrabajo.ejecutarConIdentidad(UID_ADMIN,
                () -> jdbc.queryForList("SELECT firebase_uid FROM usuarios", String.class));

        assertEquals(List.of(UID_ADMIN, UID_ADMIN), uids);   // sus dos membresías (A y B), nadie más
        assertEquals(List.of(UID_CAJERO), unidadDeTrabajo.ejecutarConIdentidad(UID_CAJERO,
                () -> jdbc.queryForList("SELECT firebase_uid FROM usuarios", String.class)));
    }

    @Test
    void conLaIdentidadSoloSeVenLasCafeteriasDondeTieneMembresia() {
        assertEquals(Set.of(CAFETERIA, CAFETERIA_B), cafeteriasVisiblesPara(UID_ADMIN));
        assertEquals(Set.of(CAFETERIA), cafeteriasVisiblesPara(UID_CAJERO));
        assertEquals(Set.of(CAFETERIA_INACTIVA), cafeteriasVisiblesPara(UID_CAJERO_INACTIVA));
        assertEquals(Set.of(), cafeteriasVisiblesPara(UID_SIN_MEMBRESIA));
    }

    @Test
    void conLaIdentidadNoSeVeNingunDatoDeNegocio() {
        unidadDeTrabajo.ejecutarConIdentidad(UID_ADMIN, () -> {
            for (String tabla : List.of("producto", "categoria", "producto_adicion", "venta", "detalle_venta",
                    "egreso", "v_movimiento")) {
                assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM " + tabla, Integer.class), tabla);
            }
            return null;
        });
    }

    @Test
    void elRepositorioListaLasCafeteriasConElRolDeCadaMembresia() {
        PgUsuarioRepository membresias = new PgUsuarioRepository(jdbc);

        List<CafeteriaDelUsuario> deLaura = unidadDeTrabajo.ejecutarConIdentidad(UID_ADMIN,
                () -> membresias.buscarCafeteriasDe(UID_ADMIN));

        assertEquals(List.of(
                new CafeteriaDelUsuario(CAFETERIA_B, NOMBRE_CAFETERIA_B, Rol.CAJERO, true),
                new CafeteriaDelUsuario(CAFETERIA, NOMBRE_CAFETERIA, Rol.ADMINISTRADOR, true)), deLaura);
        // Con la identidad de otra persona, las membresías de Laura no se ven aunque se pidan por su uid
        assertTrue(unidadDeTrabajo.ejecutarConIdentidad(UID_CAJERO,
                () -> membresias.buscarCafeteriasDe(UID_ADMIN)).isEmpty());
        // Sin identidad ni cafetería fijadas no se ve nada
        assertTrue(membresias.buscarCafeteriasDe(UID_ADMIN).isEmpty());
    }

    @Test
    void conLaCafeteriaFijadaNoSeVenMembresiasDeOtrasCafeterias() {
        assertEquals(1, unidadDeTrabajo.ejecutar(CAFETERIA, () -> jdbc.queryForObject(
                "SELECT count(*) FROM usuarios WHERE firebase_uid = ?", Integer.class, UID_ADMIN)));
        assertEquals(Set.of(CAFETERIA), Set.copyOf(unidadDeTrabajo.ejecutar(CAFETERIA,
                () -> jdbc.queryForList("SELECT id FROM cafeteria", UUID.class))));
    }

    @Test
    void lasDosVariablesNuncaQuedanFijadasALaVez() {
        // Esta prueba comparte una sola transacción: al fijar una variable, la otra se borra
        String cafeteriaTrasFijarIdentidad = unidadDeTrabajo.ejecutar(CAFETERIA, () -> {
            unidadDeTrabajo.ejecutarConIdentidad(UID_ADMIN, () -> null);
            return jdbc.queryForObject("SELECT current_setting('app.current_cafeteria', true)", String.class);
        });
        assertEquals("", cafeteriaTrasFijarIdentidad);

        unidadDeTrabajo.ejecutarConIdentidad(UID_ADMIN, () -> null);
        String[] variables = unidadDeTrabajo.ejecutar(CAFETERIA, () -> jdbc.queryForObject(
                "SELECT current_setting('app.current_cafeteria', true), current_setting('app.current_uid', true)",
                (rs, n) -> new String[]{rs.getString(1), rs.getString(2)}));
        assertEquals(CAFETERIA.toString(), variables[0]);
        assertEquals("", variables[1]);

        String[] alReves = unidadDeTrabajo.ejecutarConIdentidad(UID_ADMIN, () -> jdbc.queryForObject(
                "SELECT current_setting('app.current_cafeteria', true), current_setting('app.current_uid', true)",
                (rs, n) -> new String[]{rs.getString(1), rs.getString(2)}));
        assertEquals("", alReves[0]);
        assertEquals(UID_ADMIN, alReves[1]);
    }

    @Test
    void laMembresiaEsUnicaPorCafeteriaYPersona() {
        List<String> unicas = jdbc.queryForList("""
                SELECT string_agg(a.attname, ',' ORDER BY k.orden)
                FROM pg_constraint c
                CROSS JOIN LATERAL unnest(c.conkey) WITH ORDINALITY AS k(attnum, orden)
                JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum
                WHERE c.conrelid = 'usuarios'::regclass AND c.contype = 'u'
                GROUP BY c.oid""", String.class);

        assertTrue(unicas.contains("id_cafeteria,firebase_uid"), "Falta UNIQUE (id_cafeteria, firebase_uid): " + unicas);
        assertFalse(unicas.contains("firebase_uid"), "firebase_uid ya no puede ser único solo: " + unicas);
    }
}
