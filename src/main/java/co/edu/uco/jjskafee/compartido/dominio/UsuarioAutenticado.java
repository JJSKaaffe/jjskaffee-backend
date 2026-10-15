package co.edu.uco.jjskafee.compartido.dominio;

import java.util.Objects;
import java.util.UUID;

/**
 * Quién está haciendo la petición. Se arma con el token de Firebase y la tabla usuario,
 * nunca con datos que mande el navegador (ADR-0001, ADR-0007).
 */
public record UsuarioAutenticado(UUID id, UUID idCafeteria, String nombre, Rol rol) {

    public UsuarioAutenticado {
        Objects.requireNonNull(id);
        Objects.requireNonNull(idCafeteria);
        Objects.requireNonNull(rol);
    }

    public boolean esAdministrador() {
        return rol == Rol.ADMINISTRADOR;
    }
}
