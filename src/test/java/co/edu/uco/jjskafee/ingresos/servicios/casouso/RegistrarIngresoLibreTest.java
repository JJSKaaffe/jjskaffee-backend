package co.edu.uco.jjskafee.ingresos.servicios.casouso;

import co.edu.uco.jjskafee.RepositoriosEnMemoria;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.RecursoNoEncontradoException;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.ReglaNegocioException;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.SinPermisoException;
import co.edu.uco.jjskafee.ingresos.servicios.input.dto.request.IngresoLibreRequest;
import co.edu.uco.jjskafee.ingresos.servicios.input.dto.response.IngresoLibreResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static co.edu.uco.jjskafee.DatosPrueba.*;
import static org.junit.jupiter.api.Assertions.*;

class RegistrarIngresoLibreTest {

    private final RepositoriosEnMemoria.Catalogo catalogo = new RepositoriosEnMemoria.Catalogo()
            .categoria(EVENTOS).categoria(CAFE_EN_GRANO).categoria(CONVENIOS_ARCHIVADA).categoria(INSUMOS);
    private final RepositoriosEnMemoria.IngresosLibres ingresos = new RepositoriosEnMemoria.IngresosLibres();
    private final RepositoriosEnMemoria.Cafeterias cafeterias = new RepositoriosEnMemoria.Cafeterias().activa(CAFETERIA);
    private final RepositoriosEnMemoria.UnidadDeTrabajoDirecta uow = new RepositoriosEnMemoria.UnidadDeTrabajoDirecta();

    private RegistrarIngresoLibre casoDeUso() {
        return new RegistrarIngresoLibre(catalogo, ingresos, cafeterias, uow, RELOJ);
    }

    private static IngresoLibreRequest evento(String monto) {
        return new IngresoLibreRequest(EVENTOS.getId(), new BigDecimal(monto), "Cumpleaños de 20 personas", "FAC-77");
    }

    @Test
    void elAdministradorRegistraElIngresoEnSuCafeteria() {
        IngresoLibreResponse r = casoDeUso().registrar(evento("150000"), ADMIN);

        assertEquals(new BigDecimal("150000"), r.monto());
        assertEquals(EVENTOS.getId(), r.idCategoria());
        assertEquals("Eventos privados", r.categoria());
        assertEquals("Cumpleaños de 20 personas", r.descripcion());
        assertEquals("FAC-77", r.comprobante());
        assertEquals(ADMIN.id(), r.registradoPor());
        assertEquals(AHORA, r.fechaHora());
        assertEquals(1, ingresos.guardados.size());
        assertEquals(CAFETERIA, ingresos.guardados.get(r.idIngreso()).getIdCafeteria());
        assertEquals(CAFETERIA, uow.ultimaCafeteria);
    }

    @Test
    void elCajeroRecibeSinPermisoSinTocarLaBaseDeDatos() {
        SinPermisoException e = assertThrows(SinPermisoException.class, () -> casoDeUso().registrar(evento("1000"), CAJERO));

        assertEquals("Solo el administrador puede registrar ingresos libres", e.getMessage());
        assertNull(uow.ultimaCafeteria);
        assertTrue(ingresos.guardados.isEmpty());
    }

    @Test
    void enLaCafeteriaDondeEsCajeraLauraTampocoPuede() {
        assertThrows(SinPermisoException.class, () -> casoDeUso().registrar(evento("1000"), LAURA_EN_B));
        assertTrue(ingresos.guardados.isEmpty());
    }

    @Test
    void unaCategoriaQueNoExisteEnLaCafeteriaEsNoEncontrada() {
        IngresoLibreRequest solicitud = new IngresoLibreRequest(UUID.randomUUID(), new BigDecimal("1000"), "Evento", null);

        assertThrows(RecursoNoEncontradoException.class, () -> casoDeUso().registrar(solicitud, ADMIN));
        assertTrue(ingresos.guardados.isEmpty());
    }

    @Test
    void unaCategoriaDeEgresoOArchivadaSeRechaza() {
        for (UUID idCategoria : new UUID[]{INSUMOS.getId(), CONVENIOS_ARCHIVADA.getId()}) {
            IngresoLibreRequest solicitud = new IngresoLibreRequest(idCategoria, new BigDecimal("1000"), "Evento", null);
            assertThrows(ReglaNegocioException.class, () -> casoDeUso().registrar(solicitud, ADMIN));
        }
        assertTrue(ingresos.guardados.isEmpty());
    }

    @Test
    void unMontoConTresDecimalesNoSeRedondeaNiSeGuarda() {
        assertThrows(ReglaNegocioException.class, () -> casoDeUso().registrar(evento("1000.005"), ADMIN));
        assertTrue(ingresos.guardados.isEmpty());
    }

    @Test
    void unaCafeteriaInactivaNoRegistraIngresosLibres() {
        cafeterias.activas.clear();

        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> casoDeUso().registrar(evento("1000"), ADMIN));
        assertEquals("La cafetería no está activa; no se pueden registrar ingresos", e.getMessage());
        assertTrue(ingresos.guardados.isEmpty());
    }

    // ----- Idempotencia -----

    @Test
    void laMismaClaveYLaMismaSolicitudDevuelvenElMismoIngreso() {
        UUID clave = UUID.randomUUID();

        IngresoLibreResponse primero = casoDeUso().registrar(evento("150000"), clave, ADMIN);
        IngresoLibreResponse segundo = casoDeUso().registrar(evento("150000.00"), clave, ADMIN);

        assertEquals(primero.idIngreso(), segundo.idIngreso());
        assertEquals(1, ingresos.guardados.size());
    }

    @Test
    void laMismaClaveConOtroMontoSeRechaza() {
        UUID clave = UUID.randomUUID();
        casoDeUso().registrar(evento("150000"), clave, ADMIN);

        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> casoDeUso().registrar(evento("150001"), clave, ADMIN));
        assertTrue(e.getMessage().startsWith("Esta Idempotency-Key ya se usó para un ingreso libre"));
        assertEquals(1, ingresos.guardados.size());
    }

    @Test
    void sinClaveCadaSolicitudEsUnIngresoNuevo() {
        casoDeUso().registrar(evento("1000"), ADMIN);
        casoDeUso().registrar(evento("1000"), ADMIN);

        assertEquals(2, ingresos.guardados.size());
        ingresos.guardados.values().forEach(i -> {
            assertNull(i.getClaveIdempotencia());
            assertNull(i.getHashSolicitud());
        });
    }

    @Test
    void laHuellaIgnoraCerosDecimalesYEspaciosPeroNoOtrosCambios() {
        String base = RegistrarIngresoLibre.huellaDe(evento("1000"));

        assertEquals(64, base.length());
        assertEquals(base, RegistrarIngresoLibre.huellaDe(evento("1000.00")));
        assertEquals(base, RegistrarIngresoLibre.huellaDe(new IngresoLibreRequest(EVENTOS.getId(),
                new BigDecimal("1000"), "  Cumpleaños de 20 personas ", " FAC-77")));
        assertNotEquals(base, RegistrarIngresoLibre.huellaDe(evento("1000.01")));
        assertNotEquals(base, RegistrarIngresoLibre.huellaDe(new IngresoLibreRequest(CAFE_EN_GRANO.getId(),
                new BigDecimal("1000"), "Cumpleaños de 20 personas", "FAC-77")));
        assertNotEquals(base, RegistrarIngresoLibre.huellaDe(new IngresoLibreRequest(EVENTOS.getId(),
                new BigDecimal("1000"), "Cumpleaños de 20 personas", null)));
    }
}
