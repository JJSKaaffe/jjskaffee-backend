package co.edu.uco.jjskafee.compartido.dominio.enums;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static co.edu.uco.jjskafee.DatosPrueba.ADMIN;
import static co.edu.uco.jjskafee.DatosPrueba.LAURA_EN_B;
import static org.junit.jupiter.api.Assertions.*;

class RolTest {

    @Test
    void elAdministradorTieneTodosLosPermisos() {
        assertEquals(List.of(Permiso.REGISTRAR_VENTA, Permiso.REGISTRAR_INGRESO_LIBRE, Permiso.REGISTRAR_EGRESO,
                Permiso.VER_TODOS_LOS_MOVIMIENTOS), List.copyOf(Rol.ADMINISTRADOR.permisos()));
    }

    @Test
    void elCajeroSoloRegistraVentas() {
        assertEquals(Set.of(Permiso.REGISTRAR_VENTA), Rol.CAJERO.permisos());
        assertFalse(Rol.CAJERO.tiene(Permiso.REGISTRAR_INGRESO_LIBRE));
        assertFalse(Rol.CAJERO.tiene(Permiso.REGISTRAR_EGRESO));
        assertFalse(Rol.CAJERO.tiene(Permiso.VER_TODOS_LOS_MOVIMIENTOS));
    }

    @Test
    void losPermisosNoSePuedenModificar() {
        assertThrows(UnsupportedOperationException.class, () -> Rol.CAJERO.permisos().add(Permiso.REGISTRAR_EGRESO));
    }

    @Test
    void laMismaPersonaTienePermisosDistintosSegunLaCafeteria() {
        // Laura: administradora en A, cajera en B
        assertTrue(ADMIN.tienePermiso(Permiso.REGISTRAR_EGRESO));
        assertFalse(LAURA_EN_B.tienePermiso(Permiso.REGISTRAR_EGRESO));
        assertTrue(LAURA_EN_B.tienePermiso(Permiso.REGISTRAR_VENTA));
    }
}
