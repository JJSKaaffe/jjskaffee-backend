package co.edu.uco.jjskafee.catalogo.infraestructura.persistencia;

import co.edu.uco.jjskafee.catalogo.aplicacion.puertos.salida.RepositorioCatalogo;
import co.edu.uco.jjskafee.catalogo.dominio.Categoria;
import co.edu.uco.jjskafee.catalogo.dominio.Producto;
import co.edu.uco.jjskafee.catalogo.dominio.TipoCategoria;
import co.edu.uco.jjskafee.catalogo.dominio.TipoProducto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lectura del catálogo con JDBC. Dueño de las tablas: categoria, producto, producto_adicion.
 * Cada consulta filtra por id_cafeteria además del RLS (doble barrera · ADR-0001, SPIKE-03).
 */
public class PgRepositorioCatalogo implements RepositorioCatalogo {

    private static final String SQL_PRODUCTO = """
            SELECT id_producto, id_categoria, nombre, precio, tipo,
                   (disponible AND NOT archivado) AS vendible
              FROM producto
             WHERE id_cafeteria = ? AND id_producto = ?
            """;

    private static final String SQL_ADICIONES_DE_BASE = """
            SELECT id_adicion
              FROM producto_adicion
             WHERE id_cafeteria = ? AND id_producto_base = ?
            """;

    private static final String SQL_CATEGORIA = """
            SELECT id_categoria, nombre, tipo, archivada
              FROM categoria
             WHERE id_cafeteria = ? AND id_categoria = ?
            """;

    private static final String SQL_PRODUCTOS_VENDIBLES = """
            SELECT id_producto, id_categoria, nombre, precio, tipo, TRUE AS vendible
              FROM producto
             WHERE id_cafeteria = ? AND disponible AND NOT archivado
             ORDER BY tipo, nombre
            """;

    private static final String SQL_ADICIONES_VENDIBLES = """
            SELECT pa.id_producto_base, pa.id_adicion
              FROM producto_adicion pa
              JOIN producto a ON a.id_cafeteria = pa.id_cafeteria AND a.id_producto = pa.id_adicion
             WHERE pa.id_cafeteria = ? AND a.disponible AND NOT a.archivado
            """;

    private static final String SQL_CATEGORIAS_ACTIVAS = """
            SELECT id_categoria, nombre, tipo, archivada
              FROM categoria
             WHERE id_cafeteria = ? AND NOT archivada
             ORDER BY tipo, nombre
            """;

    private static final String SQL_CATEGORIAS_ACTIVAS_POR_TIPO = """
            SELECT id_categoria, nombre, tipo, archivada
              FROM categoria
             WHERE id_cafeteria = ? AND NOT archivada AND tipo = ?
             ORDER BY nombre
            """;

    private static final RowMapper<Categoria> MAPEO_CATEGORIA = (fila, i) -> new Categoria(
            fila.getObject("id_categoria", UUID.class),
            fila.getString("nombre"),
            TipoCategoria.valueOf(fila.getString("tipo")),
            fila.getBoolean("archivada"));

    private final JdbcTemplate jdbc;

    public PgRepositorioCatalogo(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public Optional<Producto> buscarProducto(UUID idCafeteria, UUID idProducto) {
        return jdbc.query(SQL_PRODUCTO, (fila, i) -> mapearProducto(fila, Set.of()), idCafeteria, idProducto)
                .stream().findFirst();
    }

    @Override
    public Set<UUID> adicionesPermitidas(UUID idCafeteria, UUID idProductoBase) {
        return Set.copyOf(jdbc.query(SQL_ADICIONES_DE_BASE, (fila, i) -> fila.getObject("id_adicion", UUID.class),
                idCafeteria, idProductoBase));
    }

    @Override
    public Optional<Categoria> buscarCategoria(UUID idCafeteria, UUID idCategoria) {
        return jdbc.query(SQL_CATEGORIA, MAPEO_CATEGORIA, idCafeteria, idCategoria).stream().findFirst();
    }

    @Override
    public List<Producto> listarProductosVendibles(UUID idCafeteria) {
        Map<UUID, Set<UUID>> adicionesPorBase = new HashMap<>();
        jdbc.query(SQL_ADICIONES_VENDIBLES, (RowCallbackHandler) fila -> {
            adicionesPorBase.computeIfAbsent(fila.getObject("id_producto_base", UUID.class), k -> new HashSet<>())
                    .add(fila.getObject("id_adicion", UUID.class));
        }, idCafeteria);
        return jdbc.query(SQL_PRODUCTOS_VENDIBLES, (fila, i) -> mapearProducto(fila,
                adicionesPorBase.getOrDefault(fila.getObject("id_producto", UUID.class), Set.of())), idCafeteria);
    }

    @Override
    public List<Categoria> listarCategoriasActivas(UUID idCafeteria, TipoCategoria tipo) {
        return tipo == null
                ? jdbc.query(SQL_CATEGORIAS_ACTIVAS, MAPEO_CATEGORIA, idCafeteria)
                : jdbc.query(SQL_CATEGORIAS_ACTIVAS_POR_TIPO, MAPEO_CATEGORIA, idCafeteria, tipo.name());
    }

    private static Producto mapearProducto(ResultSet fila, Set<UUID> adiciones) throws SQLException {
        return new Producto(
                fila.getObject("id_producto", UUID.class),
                fila.getObject("id_categoria", UUID.class),
                fila.getString("nombre"),
                fila.getBigDecimal("precio"),
                TipoProducto.valueOf(fila.getString("tipo")),
                fila.getBoolean("vendible"),
                adiciones);
    }
}
