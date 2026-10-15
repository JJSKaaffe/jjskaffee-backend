package co.edu.uco.jjskafee.egresos.aplicacion;

import co.edu.uco.jjskafee.compartido.dominio.excepciones.RecursoNoEncontradoException;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.SinPermisoException;
import co.edu.uco.jjskafee.egresos.aplicacion.comandos.RegistrarEgresoComando;
import co.edu.uco.jjskafee.egresos.dominio.CategoriaEgreso;
import co.edu.uco.jjskafee.egresos.dominio.Egreso;
import co.edu.uco.jjskafee.egresos.dominio.MensajesEgresos;
import co.edu.uco.jjskafee.soporte.Dobles;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static co.edu.uco.jjskafee.soporte.Dobles.ADMIN;
import static co.edu.uco.jjskafee.soporte.Dobles.AHORA;
import static co.edu.uco.jjskafee.soporte.Dobles.CAJERO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrarEgresoTest {

    private static final CategoriaEgreso INSUMOS = new CategoriaEgreso(UUID.randomUUID(), "Insumos", true, false);

    private final List<Egreso> guardados = new ArrayList<>();
    private final Dobles.UnidadDeTrabajoEnMemoria unidad = new Dobles.UnidadDeTrabajoEnMemoria();
    private final RegistrarEgreso casoUso = new RegistrarEgreso(
            (idCafeteria, idCategoria) -> INSUMOS.id().equals(idCategoria) ? Optional.of(INSUMOS) : Optional.empty(),
            guardados::add, unidad, Dobles.relojFijo(AHORA));

    @Test
    void registraYGuarda() {
        Egreso egreso = casoUso.registrar(
                new RegistrarEgresoComando(INSUMOS.id(), new BigDecimal("30000"), "Azúcar y vasos", "PRB-1"), ADMIN);

        assertEquals(List.of(egreso), guardados);
        assertEquals(List.of(ADMIN.idCafeteria()), unidad.cafeterias);
    }

    @Test
    void elCajeroEsRechazadoAntesDeTocarLaBaseDeDatos() {
        assertThrows(SinPermisoException.class, () -> casoUso.registrar(
                new RegistrarEgresoComando(INSUMOS.id(), BigDecimal.TEN, "x", null), CAJERO));
        assertTrue(unidad.cafeterias.isEmpty());
    }

    @Test
    void unaCategoriaQueNoExisteResponde404() {
        RecursoNoEncontradoException error = assertThrows(RecursoNoEncontradoException.class, () -> casoUso.registrar(
                new RegistrarEgresoComando(UUID.randomUUID(), BigDecimal.TEN, "x", null), ADMIN));
        assertEquals(MensajesEgresos.CATEGORIA_NO_EXISTE, error.getCodigo());
    }
}
