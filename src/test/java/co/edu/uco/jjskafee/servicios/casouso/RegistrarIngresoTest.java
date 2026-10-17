package co.edu.uco.jjskafee.servicios.casouso;

import co.edu.uco.jjskafee.dominio.enums.EstadoVenta;
import co.edu.uco.jjskafee.dominio.excepciones.RecursoNoEncontradoException;
import co.edu.uco.jjskafee.servicios.input.dto.request.AdicionRequest;
import co.edu.uco.jjskafee.servicios.input.dto.request.IngresoRequest;
import co.edu.uco.jjskafee.servicios.input.dto.request.ItemRequest;
import co.edu.uco.jjskafee.servicios.input.dto.response.IngresoResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static co.edu.uco.jjskafee.DatosPrueba.*;
import static org.junit.jupiter.api.Assertions.*;

class RegistrarIngresoTest {

    private final RepositoriosEnMemoria.Catalogo catalogo = new RepositoriosEnMemoria.Catalogo()
            .producto(LATTE).producto(PANDEBONO).producto(ALMENDRA).producto(SHOT)
            .adiciones(LATTE, ADICIONES_LATTE);
    private final RepositoriosEnMemoria.Ventas ventas = new RepositoriosEnMemoria.Ventas();
    private final RepositoriosEnMemoria.UnidadDeTrabajoDirecta uow = new RepositoriosEnMemoria.UnidadDeTrabajoDirecta();

    private IngresoRequest ventaDePrueba() {
        return new IngresoRequest(List.of(
                new ItemRequest(LATTE.getId(), 2, List.of(new AdicionRequest(ALMENDRA.getId(), null))),
                new ItemRequest(PANDEBONO.getId(), 1, null)));
    }

    @Test
    void elTotalLoCalculaElServidor() {
        RegistrarIngreso casoDeUso = new RegistrarIngreso(catalogo, ventas, uow, RELOJ);

        IngresoResponse r = casoDeUso.registrar(ventaDePrueba(), CAJERO);

        assertEquals(new BigDecimal("17000"), r.total());
        assertEquals(2, r.items().size());
        assertEquals(1, r.items().get(0).adiciones().size());
        assertEquals(1, ventas.guardadas.size());
        assertEquals(CAJERO.id(), r.registradoPor());
    }

    @Test
    void productoInexistente() {
        RegistrarIngreso casoDeUso = new RegistrarIngreso(catalogo, ventas, uow, RELOJ);
        IngresoRequest solicitud = new IngresoRequest(List.of(new ItemRequest(UUID.randomUUID(), 1, null)));

        assertThrows(RecursoNoEncontradoException.class, () -> casoDeUso.registrar(solicitud, CAJERO));
        assertTrue(ventas.guardadas.isEmpty());
    }

    @Test
    void deshacerAnulaLaVenta() {
        RegistrarIngreso alVender = new RegistrarIngreso(catalogo, ventas, uow, RELOJ);
        IngresoResponse r = alVender.registrar(ventaDePrueba(), CAJERO);

        Clock treintaSegundosDespues = Clock.fixed(AHORA.plusSeconds(30), ZoneOffset.UTC);
        new RegistrarIngreso(catalogo, ventas, uow, treintaSegundosDespues).deshacer(r.idVenta(), CAJERO);

        assertEquals(EstadoVenta.ANULADA, ventas.guardadas.get(r.idVenta()).getEstado());
    }
}
