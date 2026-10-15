package co.edu.uco.jjskafee.compartido.aplicacion.puertos;

import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;

import java.util.Optional;
import java.util.UUID;

/**
 * Lee la tabla usuario (la mantiene el módulo externo de gestión de usuarios).
 * Así un permiso revocado se aplica de inmediato, sin esperar a que caduque el token.
 */
public interface RepositorioUsuarios {

    Optional<UsuarioAutenticado> buscarActivoPorUid(UUID idCafeteria, String firebaseUid);
}
