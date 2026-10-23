package co.edu.uco.jjskafee.egresos.servicios.casouso;

import co.edu.uco.jjskafee.RepositoriosEnMemoria;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.RecursoNoEncontradoException;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.SinPermisoException;
import co.edu.uco.jjskafee.egresos.servicios.input.dto.request.EgresoRequest;
import co.edu.uco.jjskafee.egresos.servicios.input.dto.response.EgresoResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static co.edu.uco.jjskafee.DatosPrueba.*;
import static org.junit.jupiter.api.Assertions.*;

class RegistrarEgresoTest {

    private final RepositoriosEnMemoria.Catalogo catalogo = new RepositoriosEnMemoria.Catalogo().categoria(INSUMOS);
    private final RepositoriosEnMemoria.Egresos egresos = new RepositoriosEnMemoria.Egresos();
    private final RepositoriosEnMemoria.UnidadDeTrabajoDirecta uow = new RepositoriosEnMemoria.UnidadDeTrabajoDirecta();
    private final RegistrarEgreso casoDeUso = new RegistrarEgreso(catalogo, egresos, uow, RELOJ);

    @Test
    void guardaElEgresoYDevuelveLaRespuesta() {
        EgresoResponse r = casoDeUso.registrar(
                new EgresoRequest(INSUMOS.getId(), new BigDecimal("38000"), "Compra de azúcar", "FAC-9"), ADMIN);

        assertEquals(1, egresos.guardados.size());
        assertEquals("Insumos", r.categoria());
        assertEquals(new BigDecimal("38000"), r.monto());
        assertEquals(ADMIN.id(), r.registradoPor());
        assertEquals(AHORA, r.fechaHora());
        assertEquals(CAFETERIA, uow.ultimaCafeteria);   // se activó el aislamiento de la cafetería
    }

    @Test
    void elCajeroRecibeSinPermisoYNoSeGuardaNada() {
        assertThrows(SinPermisoException.class, () -> casoDeUso.registrar(
                new EgresoRequest(INSUMOS.getId(), new BigDecimal("1000"), "x", null), CAJERO));
        assertTrue(egresos.guardados.isEmpty());
    }

    @Test
    void categoriaInexistente() {
        assertThrows(RecursoNoEncontradoException.class, () -> casoDeUso.registrar(
                new EgresoRequest(UUID.randomUUID(), new BigDecimal("1000"), "x", null), ADMIN));
    }
}
