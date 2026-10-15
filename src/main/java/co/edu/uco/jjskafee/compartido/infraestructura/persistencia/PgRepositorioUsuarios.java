package co.edu.uco.jjskafee.compartido.infraestructura.persistencia;

import co.edu.uco.jjskafee.compartido.aplicacion.puertos.RepositorioUsuarios;
import co.edu.uco.jjskafee.compartido.dominio.Rol;
import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Lee la tabla usuario (solo lectura; la escribe el módulo externo de gestión de usuarios). */
public class PgRepositorioUsuarios implements RepositorioUsuarios {

    private static final String SQL_BUSCAR_ACTIVO = """
            SELECT id_usuario, id_cafeteria, nombre, apellido, rol
              FROM usuario
             WHERE id_cafeteria = ?
               AND firebase_uid = ?
               AND activo
            """;

    private final JdbcTemplate jdbc;

    public PgRepositorioUsuarios(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public Optional<UsuarioAutenticado> buscarActivoPorUid(UUID idCafeteria, String firebaseUid) {
        return jdbc.query(SQL_BUSCAR_ACTIVO, (fila, i) -> new UsuarioAutenticado(
                        fila.getObject("id_usuario", UUID.class),
                        fila.getObject("id_cafeteria", UUID.class),
                        fila.getString("nombre") + " " + fila.getString("apellido"),
                        Rol.valueOf(fila.getString("rol"))),
                idCafeteria, firebaseUid).stream().findFirst();
    }
}
