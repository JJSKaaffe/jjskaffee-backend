package co.edu.uco.jjskafee.infraestructura.output.repositorios.estructurados;

import co.edu.uco.jjskafee.dominio.Categoria;
import co.edu.uco.jjskafee.dominio.Producto;
import co.edu.uco.jjskafee.dominio.enums.TipoCategoria;
import co.edu.uco.jjskafee.dominio.enums.TipoProducto;
import co.edu.uco.jjskafee.servicios.output.ports.CatalogoRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Lee productos, adiciones y categorías (solo lectura). RLS deja ver solo los de la cafetería activa. */
public class PgCatalogoRepository implements CatalogoRepository {

    private final JdbcTemplate jdbc;

    public PgCatalogoRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public Optional<Producto> buscarProducto(UUID idProducto) {
        return jdbc.query("""
                        SELECT id, nombre, precio, tipo, disponible
                        FROM producto
                        WHERE id = ?""",
                (rs, fila) -> new Producto(
                        rs.getObject("id", UUID.class),
                        rs.getString("nombre"),
                        rs.getBigDecimal("precio"),
                        TipoProducto.valueOf(rs.getString("tipo")),
                        rs.getBoolean("disponible")),
                idProducto).stream().findFirst();
    }

    @Override
    public Set<UUID> adicionesPermitidas(UUID idProductoBase) {
        return new HashSet<>(jdbc.queryForList(
                "SELECT id_adicion FROM producto_adicion WHERE id_producto_base = ?", UUID.class, idProductoBase));
    }

    @Override
    public Optional<Categoria> buscarCategoria(UUID idCategoria) {
        return jdbc.query("""
                        SELECT id, nombre, tipo, archivada
                        FROM categoria
                        WHERE id = ?""",
                (rs, fila) -> new Categoria(
                        rs.getObject("id", UUID.class),
                        rs.getString("nombre"),
                        TipoCategoria.valueOf(rs.getString("tipo")),
                        rs.getBoolean("archivada")),
                idCategoria).stream().findFirst();
    }
}
