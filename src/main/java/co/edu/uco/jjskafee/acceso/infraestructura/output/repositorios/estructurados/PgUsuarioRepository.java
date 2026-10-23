package co.edu.uco.jjskafee.acceso.infraestructura.output.repositorios.estructurados;

import co.edu.uco.jjskafee.acceso.dominio.CafeteriaDelUsuario;
import co.edu.uco.jjskafee.acceso.servicios.output.ports.MembresiaRepository;
import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.compartido.dominio.enums.Rol;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Lee la tabla usuarios, que guarda membresías: una fila por persona y cafetería, con su rol
 * (la llena el módulo de autenticación). Solo lectura; siempre dentro de UnidadDeTrabajo.
 */
public class PgUsuarioRepository implements MembresiaRepository {

    private final JdbcTemplate jdbc;

    public PgUsuarioRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    /** Con la cafetería fijada: la UNIQUE (id_cafeteria, firebase_uid) deja a lo sumo una fila. */
    @Override
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

    /**
     * Con la identidad fijada (app.current_uid): RLS solo deja ver las membresías de esa persona y las
     * cafeterías donde tiene una activa. El filtro por firebase_uid se repite como defensa adicional.
     */
    @Override
    public List<CafeteriaDelUsuario> buscarCafeteriasDe(String firebaseUid) {
        return jdbc.query("""
                        SELECT c.id, c.nombre, c.activo, u.rol
                        FROM usuarios u
                        JOIN cafeteria c ON c.id = u.id_cafeteria
                        WHERE u.firebase_uid = ? AND u.activo
                        ORDER BY c.nombre, c.id""",
                (rs, n) -> new CafeteriaDelUsuario(
                        rs.getObject("id", UUID.class),
                        rs.getString("nombre"),
                        Rol.valueOf(rs.getString("rol")),
                        rs.getBoolean("activo")),
                firebaseUid);
    }
}
