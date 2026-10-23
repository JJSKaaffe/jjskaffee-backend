package co.edu.uco.jjskafee.acceso.servicios.casouso;

import co.edu.uco.jjskafee.RepositoriosEnMemoria;
import co.edu.uco.jjskafee.acceso.dominio.Identidad;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.NoAutenticadoException;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.SinPermisoException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static co.edu.uco.jjskafee.DatosPrueba.*;
import static org.junit.jupiter.api.Assertions.*;

class AutenticarUsuarioTest {

    private final RepositoriosEnMemoria.UnidadDeTrabajoDirecta unidadDeTrabajo = new RepositoriosEnMemoria.UnidadDeTrabajoDirecta();
    private final RepositoriosEnMemoria.Membresias membresias = new RepositoriosEnMemoria.Membresias(unidadDeTrabajo)
            .activa(UID_ADMIN, ADMIN, NOMBRE_CAFETERIA)
            .activa(UID_ADMIN, LAURA_EN_B, NOMBRE_CAFETERIA_B)
            .activa(UID_CAJERO, CAJERO, NOMBRE_CAFETERIA)
            .inactiva(UID_CAJERO_B, CAJERO_B, NOMBRE_CAFETERIA_B);

    /** Verificador falso: el token es "token-" + uid. */
    private final AutenticarUsuario casoDeUso = new AutenticarUsuario(token -> {
        if (token == null || !token.startsWith("token-")) {
            throw new NoAutenticadoException("El token no es válido o ya venció");
        }
        return new Identidad(token.substring("token-".length()));
    }, membresias, unidadDeTrabajo);

    @Test
    void identificarDevuelveSoloLaIdentidadDelToken() {
        assertEquals(new Identidad(UID_ADMIN), casoDeUso.identificar("token-" + UID_ADMIN));
        assertThrows(NoAutenticadoException.class, () -> casoDeUso.identificar("inventado"));
    }

    @Test
    void elRolSaleDeLaMembresiaEnLaCafeteriaElegida() {
        Identidad laura = new Identidad(UID_ADMIN);

        assertEquals(ADMIN, casoDeUso.autorizarEnCafeteria(laura, CAFETERIA));
        assertEquals(CAFETERIA, unidadDeTrabajo.ultimaCafeteria);   // se consultó con RLS de A

        assertEquals(LAURA_EN_B, casoDeUso.autorizarEnCafeteria(laura, CAFETERIA_B));
        assertEquals(CAFETERIA_B, unidadDeTrabajo.ultimaCafeteria);
    }

    @Test
    void sinMembresiaEnEsaCafeteriaEsSinPermiso() {
        SinPermisoException e = assertThrows(SinPermisoException.class,
                () -> casoDeUso.autorizarEnCafeteria(new Identidad(UID_CAJERO), CAFETERIA_B));
        assertEquals("No tienes acceso a esta cafetería", e.getMessage());

        assertThrows(SinPermisoException.class,
                () -> casoDeUso.autorizarEnCafeteria(new Identidad(UID_SIN_MEMBRESIA), CAFETERIA));
    }

    @Test
    void unaCafeteriaQueNoExisteResponde403ComoSiNoPerteneciera() {
        SinPermisoException e = assertThrows(SinPermisoException.class,
                () -> casoDeUso.autorizarEnCafeteria(new Identidad(UID_ADMIN), UUID.randomUUID()));
        assertEquals("No tienes acceso a esta cafetería", e.getMessage());
    }

    @Test
    void unaMembresiaDesactivadaNoDaAcceso() {
        assertThrows(SinPermisoException.class,
                () -> casoDeUso.autorizarEnCafeteria(new Identidad(UID_CAJERO_B), CAFETERIA_B));
    }
}
