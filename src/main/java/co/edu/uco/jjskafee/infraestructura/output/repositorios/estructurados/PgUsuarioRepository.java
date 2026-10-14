package co.edu.uco.jjskafee.infraestructura.output.repositorios.estructurados;

import co.edu.uco.jjskafee.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.dominio.enums.Rol;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Lee la tabla usuarios (administrador y cajeros; la llena el módulo de autenticación). Debe usarse dentro de
 * UnidadDeTrabajo con la cafetería del token: si el usuario es de otra cafetería, RLS no lo deja ver.
 */
public class PgUsuarioRepository {

    private final JdbcTemplate jdbc;

    public PgUsuarioRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    public Optional<UsuarioAutenticado> buscarActivoPorFirebaseUid(String firebaseUid) {
        return jdbc.query("""
                        SELECT id, id_cafeteria, nombre, rol
                        FROM usuarios
                        WHERE firebase_uid = ? AND activo""",
                (rs, n) -> new UsuarioAutenticado(
                        rs.getObject("id", UUID.class),
                        rs.getObject("id_cafeteria", UUID.class),
                        rs.getString("nombre"),
                        Rol.valueOf(rs.getString("rol"))),
                firebaseUid).stream().findFirst();
    }
}
