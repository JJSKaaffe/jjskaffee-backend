package co.edu.uco.jjskafee.acceso.servicios.input.dto.response;

import co.edu.uco.jjskafee.acceso.dominio.CafeteriaDelUsuario;
import co.edu.uco.jjskafee.compartido.dominio.enums.Rol;

import java.util.UUID;

/** Una cafetería del usuario para la pantalla "elige tu cafetería". */
public record CafeteriaDelUsuarioResponse(UUID idCafeteria, String nombre, Rol rol, boolean activa) {

    public static CafeteriaDelUsuarioResponse desde(CafeteriaDelUsuario cafeteria) {
        return new CafeteriaDelUsuarioResponse(cafeteria.idCafeteria(), cafeteria.nombre(), cafeteria.rol(),
                cafeteria.activa());
    }
}
