package co.edu.uco.jjskafee.acceso.servicios.casouso;

import co.edu.uco.jjskafee.RepositoriosEnMemoria;
import co.edu.uco.jjskafee.acceso.dominio.Identidad;
import co.edu.uco.jjskafee.acceso.servicios.input.dto.response.CafeteriaDelUsuarioResponse;
import co.edu.uco.jjskafee.acceso.servicios.input.dto.response.UsuarioActualResponse;
import co.edu.uco.jjskafee.compartido.dominio.enums.Permiso;
import co.edu.uco.jjskafee.compartido.dominio.enums.Rol;
import org.junit.jupiter.api.Test;

import java.util.List;

import static co.edu.uco.jjskafee.DatosPrueba.*;
import static org.junit.jupiter.api.Assertions.*;

class ConsultarAccesoTest {

    private final RepositoriosEnMemoria.UnidadDeTrabajoDirecta unidadDeTrabajo = new RepositoriosEnMemoria.UnidadDeTrabajoDirecta();
    private final RepositoriosEnMemoria.Membresias membresias = new RepositoriosEnMemoria.Membresias(unidadDeTrabajo)
            .activa(UID_ADMIN, ADMIN, NOMBRE_CAFETERIA)
            .activa(UID_ADMIN, LAURA_EN_B, NOMBRE_CAFETERIA_B)
            .activa(UID_CAJERO, CAJERO, NOMBRE_CAFETERIA)
            .enCafeteriaInactiva(UID_CAJERO_INACTIVA, CAJERO_INACTIVA, "Café Cerrado (C, inactiva)");
    private final ConsultarAcceso casoDeUso = new ConsultarAcceso(membresias, unidadDeTrabajo);

    @Test
    void lauraVeSusDosCafeteriasConUnRolDistintoEnCadaUna() {
        List<CafeteriaDelUsuarioResponse> cafeterias = casoDeUso.misCafeterias(new Identidad(UID_ADMIN));

        assertEquals(List.of(
                new CafeteriaDelUsuarioResponse(CAFETERIA, NOMBRE_CAFETERIA, Rol.ADMINISTRADOR, true),
                new CafeteriaDelUsuarioResponse(CAFETERIA_B, NOMBRE_CAFETERIA_B, Rol.CAJERO, true)), cafeterias);
    }

    @Test
    void laListaSeConsultaConLaIdentidadYSinCafeteria() {
        casoDeUso.misCafeterias(new Identidad(UID_CAJERO));

        assertEquals(UID_CAJERO, unidadDeTrabajo.ultimaIdentidad);
        assertNull(unidadDeTrabajo.ultimaCafeteria);
    }

    @Test
    void unaCafeteriaInactivaApareceMarcadaComoNoActiva() {
        assertEquals(List.of(new CafeteriaDelUsuarioResponse(CAFETERIA_INACTIVA, "Café Cerrado (C, inactiva)",
                Rol.CAJERO, false)), casoDeUso.misCafeterias(new Identidad(UID_CAJERO_INACTIVA)));
    }

    @Test
    void sinMembresiasLaListaEstaVacia() {
        assertTrue(casoDeUso.misCafeterias(new Identidad(UID_SIN_MEMBRESIA)).isEmpty());
    }

    @Test
    void yoDevuelveElRolYLosPermisosDeLaCafeteriaElegida() {
        assertEquals(new UsuarioActualResponse(ADMIN.id(), "Laura Gómez", Rol.ADMINISTRADOR, CAFETERIA,
                        List.of(Permiso.REGISTRAR_VENTA, Permiso.REGISTRAR_INGRESO_LIBRE, Permiso.REGISTRAR_EGRESO,
                                Permiso.VER_TODOS_LOS_MOVIMIENTOS)),
                casoDeUso.yo(ADMIN));
        assertEquals(new UsuarioActualResponse(LAURA_EN_B.id(), "Laura Gómez", Rol.CAJERO, CAFETERIA_B,
                        List.of(Permiso.REGISTRAR_VENTA)),
                casoDeUso.yo(LAURA_EN_B));
    }
}
