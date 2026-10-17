package co.edu.uco.jjskafee.dominio;

import co.edu.uco.jjskafee.dominio.enums.Rol;

import java.util.Objects;
import java.util.UUID;

/**
 * Quién está haciendo la petición. Se arma a partir del token de Firebase,
 * nunca con datos que mande el navegador (ADR-0001, ADR-0007).
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
}
