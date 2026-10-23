package co.edu.uco.jjskafee.ingresos.infraestructura.output.repositorios.estructurados;

import co.edu.uco.jjskafee.compartido.dominio.Categoria;
import co.edu.uco.jjskafee.compartido.dominio.Producto;
import co.edu.uco.jjskafee.compartido.dominio.enums.TipoCategoria;
import co.edu.uco.jjskafee.compartido.dominio.enums.TipoProducto;
import co.edu.uco.jjskafee.ingresos.dominio.ProductoDelMenu;
import co.edu.uco.jjskafee.ingresos.servicios.output.ports.CatalogoDeVentaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Lee el catálogo de la pantalla de venta (solo lectura). RLS deja ver solo el de la cafetería activa.
 * Los productos con sus adiciones salen en una sola consulta (LEFT JOIN), no una por producto.
 */
public class PgCatalogoDeVentaRepository implements CatalogoDeVentaRepository {

    private final JdbcTemplate jdbc;

    public PgCatalogoDeVentaRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public List<ProductoDelMenu> productosParaVender() {
        // Una fila por base y adición permitida (o una sola fila con la adición en NULL si no tiene)
        Map<Producto, List<Producto>> productos = new LinkedHashMap<>();
        jdbc.query("""
                        SELECT b.id, b.nombre, b.precio,
                               a.id AS id_adicion, a.nombre AS nombre_adicion, a.precio AS precio_adicion
                        FROM producto b
                        LEFT JOIN producto_adicion pa
                               ON pa.id_cafeteria = b.id_cafeteria AND pa.id_producto_base = b.id
                        LEFT JOIN producto a
                               ON a.id_cafeteria = pa.id_cafeteria AND a.id = pa.id_adicion
                              AND a.tipo = 'ADICION' AND a.disponible AND a.precio > 0
                        WHERE b.tipo = 'BASE' AND b.disponible AND b.precio > 0
                        ORDER BY b.nombre, b.id, a.nombre, a.id""",
                (RowCallbackHandler) rs -> {
                    Producto base = new Producto(rs.getObject("id", UUID.class), rs.getString("nombre"),
                            rs.getBigDecimal("precio"), TipoProducto.BASE, true);
                    List<Producto> adiciones = productos.computeIfAbsent(base, b -> new ArrayList<>());
                    UUID idAdicion = rs.getObject("id_adicion", UUID.class);
                    if (idAdicion != null) {
                        adiciones.add(new Producto(idAdicion, rs.getString("nombre_adicion"),
                                rs.getBigDecimal("precio_adicion"), TipoProducto.ADICION, true));
                    }
                });
        return productos.entrySet().stream()
                .map(p -> new ProductoDelMenu(p.getKey(), p.getValue()))
                .toList();
    }

    @Override
    public List<Categoria> categoriasDeIngresoActivas() {
        return jdbc.query("""
                        SELECT id, nombre
                        FROM categoria
                        WHERE tipo = 'INGRESO' AND NOT archivada
                        ORDER BY nombre, id""",
                (rs, n) -> new Categoria(rs.getObject("id", UUID.class), rs.getString("nombre"),
                        TipoCategoria.INGRESO, false));
    }
}
