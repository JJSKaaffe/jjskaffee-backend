package co.edu.uco.jjskafee.servicios.casouso;

import co.edu.uco.jjskafee.dominio.Venta;
import co.edu.uco.jjskafee.dominio.enums.EstadoVenta;
import co.edu.uco.jjskafee.dominio.excepciones.RecursoNoEncontradoException;
import co.edu.uco.jjskafee.dominio.excepciones.ReglaNegocioException;
import co.edu.uco.jjskafee.servicios.input.dto.request.AdicionRequest;
import co.edu.uco.jjskafee.servicios.input.dto.request.IngresoRequest;
import co.edu.uco.jjskafee.servicios.input.dto.request.ItemRequest;
import co.edu.uco.jjskafee.servicios.input.dto.response.IngresoResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static co.edu.uco.jjskafee.DatosPrueba.*;
import static org.junit.jupiter.api.Assertions.*;

class RegistrarIngresoTest {

    private final RepositoriosEnMemoria.Catalogo catalogo = new RepositoriosEnMemoria.Catalogo()
            .producto(LATTE).producto(PANDEBONO).producto(ALMENDRA).producto(SHOT)
            .adiciones(LATTE, ADICIONES_LATTE);
    private final RepositoriosEnMemoria.Ventas ventas = new RepositoriosEnMemoria.Ventas();
    private final RepositoriosEnMemoria.Cafeterias cafeterias = new RepositoriosEnMemoria.Cafeterias().activa(CAFETERIA);
    private final RepositoriosEnMemoria.UnidadDeTrabajoDirecta uow = new RepositoriosEnMemoria.UnidadDeTrabajoDirecta();

    private RegistrarIngreso casoDeUso() {
        return new RegistrarIngreso(catalogo, ventas, cafeterias, uow, RELOJ);
    }

    private IngresoRequest ventaDePrueba() {
        return new IngresoRequest(List.of(
                new ItemRequest(LATTE.getId(), 2, List.of(new AdicionRequest(ALMENDRA.getId(), null))),
                new ItemRequest(PANDEBONO.getId(), 1, null)));
    }

    private static IngresoRequest pandebonos(int cantidad) {
        return new IngresoRequest(List.of(new ItemRequest(PANDEBONO.getId(), cantidad, null)));
    }

    @Test
    void elTotalLoCalculaElServidor() {
        IngresoResponse r = casoDeUso().registrar(ventaDePrueba(), CAJERO);

        assertEquals(new BigDecimal("17000"), r.total());
        assertEquals(2, r.items().size());
        assertEquals(1, r.items().get(0).adiciones().size());
        assertEquals(1, ventas.guardadas.size());
        assertEquals(CAJERO.id(), r.registradoPor());
        assertEquals(CAFETERIA, uow.ultimaCafeteria);
    }

    @Test
    void productoInexistente() {
        IngresoRequest solicitud = new IngresoRequest(List.of(new ItemRequest(UUID.randomUUID(), 1, null)));

        assertThrows(RecursoNoEncontradoException.class, () -> casoDeUso().registrar(solicitud, CAJERO));
        assertTrue(ventas.guardadas.isEmpty());
    }

    @Test
    void deshacerAnulaLaVenta() {
        IngresoResponse r = casoDeUso().registrar(ventaDePrueba(), CAJERO);

        Clock treintaSegundosDespues = Clock.fixed(AHORA.plusSeconds(30), ZoneOffset.UTC);
        new RegistrarIngreso(catalogo, ventas, cafeterias, uow, treintaSegundosDespues).deshacer(r.idVenta(), CAJERO);

        assertEquals(EstadoVenta.ANULADA, ventas.guardadas.get(r.idVenta()).getEstado());
    }

    // ----- Cafetería activa -----

    @Test
    void unaCafeteriaInactivaNoRegistraIngresos() {
        cafeterias.activas.clear();

        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> casoDeUso().registrar(ventaDePrueba(), CAJERO));
        assertEquals("La cafetería no está activa; no se pueden registrar ingresos", e.getMessage());
        assertTrue(ventas.guardadas.isEmpty());
    }

    @Test
    void laCafeteriaInactivaSeRechazaAunqueReintenteConLaMismaClave() {
        UUID clave = UUID.randomUUID();
        casoDeUso().registrar(ventaDePrueba(), clave, CAJERO);
        cafeterias.activas.clear();

        assertThrows(ReglaNegocioException.class, () -> casoDeUso().registrar(ventaDePrueba(), clave, CAJERO));
    }

    // ----- Reglas del dominio vistas desde el caso de uso -----

    @Test
    void adicionesRepetidasQueSumanMasDe99SeRechazan() {
        IngresoRequest solicitud = new IngresoRequest(List.of(new ItemRequest(LATTE.getId(), 1, List.of(
                new AdicionRequest(SHOT.getId(), 50), new AdicionRequest(SHOT.getId(), 50)))));

        assertThrows(ReglaNegocioException.class, () -> casoDeUso().registrar(solicitud, CAJERO));
        assertTrue(ventas.guardadas.isEmpty());
    }

    // ----- Idempotencia (Idempotency-Key) -----

    @Test
    void laMismaClaveDosVecesDevuelveLaMismaVentaSinCrearOtra() {
        UUID clave = UUID.randomUUID();

        IngresoResponse primera = casoDeUso().registrar(ventaDePrueba(), clave, CAJERO);
        IngresoResponse segunda = casoDeUso().registrar(ventaDePrueba(), clave, CAJERO);

        assertEquals(primera.idVenta(), segunda.idVenta());
        assertEquals(primera.total(), segunda.total());
        assertEquals(1, ventas.guardadas.size());
        assertEquals(clave, ventas.guardadas.get(primera.idVenta()).getClaveIdempotencia());
    }

    @Test
    void laMismaClaveConOtroCuerpoResponde422YNoCreaOtraVenta() {
        UUID clave = UUID.randomUUID();
        casoDeUso().registrar(pandebonos(1), clave, CAJERO);

        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> casoDeUso().registrar(pandebonos(2), clave, CAJERO));
        assertEquals("Esta Idempotency-Key ya se usó para una venta con otros productos o cantidades; "
                + "para registrar una venta nueva usa una clave nueva", e.getMessage());
        assertEquals(1, ventas.guardadas.size());
    }

    @Test
    void clavesDistintasOSinClaveSonVentasDistintas() {
        casoDeUso().registrar(ventaDePrueba(), UUID.randomUUID(), CAJERO);
        casoDeUso().registrar(ventaDePrueba(), UUID.randomUUID(), CAJERO);
        casoDeUso().registrar(ventaDePrueba(), CAJERO);
        casoDeUso().registrar(ventaDePrueba(), CAJERO);

        assertEquals(4, ventas.guardadas.size());
    }

    @Test
    void siOtraPeticionConLaMismaClaveGanaLaCarreraSeDevuelveEsa() {
        // Simula dos peticiones simultáneas: la primera búsqueda no encuentra nada, pero al guardar
        // la "otra" ya está (en la BD sería el ON CONFLICT de la UNIQUE por cafetería)
        UUID clave = UUID.randomUUID();
        Venta ganadora = Venta.crear(CAJERO, clave, RegistrarIngreso.huellaDe(ventaDePrueba()), AHORA);
        ganadora.agregar(PANDEBONO, 1, Map.of(), Set.of());
        RepositoriosEnMemoria.Ventas conCarrera = new RepositoriosEnMemoria.Ventas() {
            private boolean primeraBusqueda = true;
            @Override
            public Optional<Venta> buscarPorClaveIdempotencia(UUID c) {
                if (primeraBusqueda) {
                    primeraBusqueda = false;
                    guardadas.put(ganadora.getId(), ganadora);   // la otra petición guarda justo ahora
                    return Optional.empty();
                }
                return super.buscarPorClaveIdempotencia(c);
            }
        };

        IngresoResponse r = new RegistrarIngreso(catalogo, conCarrera, cafeterias, uow, RELOJ)
                .registrar(ventaDePrueba(), clave, CAJERO);

        assertEquals(ganadora.getId(), r.idVenta());
        assertEquals(1, conCarrera.guardadas.size());
    }

    @Test
    void laHuellaDependeDeProductosCantidadesYAdiciones() {
        String base = RegistrarIngreso.huellaDe(ventaDePrueba());

        assertEquals(64, base.length());
        assertEquals(base, RegistrarIngreso.huellaDe(ventaDePrueba()));
        // Adición sin cantidad = cantidad 1
        assertEquals(base, RegistrarIngreso.huellaDe(new IngresoRequest(List.of(
                new ItemRequest(LATTE.getId(), 2, List.of(new AdicionRequest(ALMENDRA.getId(), 1))),
                new ItemRequest(PANDEBONO.getId(), 1, null)))));
        assertNotEquals(base, RegistrarIngreso.huellaDe(new IngresoRequest(List.of(
                new ItemRequest(LATTE.getId(), 2, List.of(new AdicionRequest(SHOT.getId(), null))),
                new ItemRequest(PANDEBONO.getId(), 1, null)))));
        assertNotEquals(RegistrarIngreso.huellaDe(pandebonos(1)), RegistrarIngreso.huellaDe(pandebonos(2)));
    }
}
