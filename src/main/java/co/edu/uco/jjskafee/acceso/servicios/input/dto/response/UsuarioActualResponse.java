package co.edu.uco.jjskafee.acceso.servicios.input.dto.response;

import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.compartido.dominio.enums.Permiso;
import co.edu.uco.jjskafee.compartido.dominio.enums.Rol;

import java.util.List;
import java.util.UUID;

/** Respuesta de GET /acceso/yo: el frontend muestra el menú según los permisos. */
public record UsuarioActualResponse(UUID id, String nombre, Rol rol, UUID idCafeteria, List<Permiso> permisos) {

    public static UsuarioActualResponse desde(UsuarioAutenticado usuario) {
        return new UsuarioActualResponse(usuario.id(), usuario.nombre(), usuario.rol(), usuario.idCafeteria(),
                List.copyOf(usuario.rol().permisos()));
    }
}
