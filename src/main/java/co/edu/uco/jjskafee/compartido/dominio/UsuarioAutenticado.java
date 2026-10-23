package co.edu.uco.jjskafee.compartido.dominio;

import co.edu.uco.jjskafee.compartido.dominio.enums.Permiso;
import co.edu.uco.jjskafee.compartido.dominio.enums.Rol;

import java.util.Objects;
import java.util.UUID;

/**
 * Quién está haciendo la petición y en qué cafetería. La identidad sale del token de Firebase;
 * la cafetería, del encabezado X-Cafeteria-Id, y el id, nombre y rol, de su membresía en esa
 * cafetería (tabla usuarios). Nunca con datos del cuerpo de la petición (ADR-0007, ADR-0016).
 */
public record UsuarioAutenticado(UUID id, UUID idCafeteria, String nombre, Rol rol) {

    public UsuarioAutenticado {
        Objects.requireNonNull(id, "El id del usuario es obligatorio");
        Objects.requireNonNull(idCafeteria, "La cafetería del usuario es obligatoria");
        Objects.requireNonNull(rol, "El rol del usuario es obligatorio");
    }

    public boolean esAdministrador() {
        return rol == Rol.ADMINISTRADOR;
    }

    public boolean tienePermiso(Permiso permiso) {
        return rol.tiene(permiso);
    }
}
