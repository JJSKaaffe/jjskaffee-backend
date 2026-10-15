package co.edu.uco.jjskafee.catalogo.aplicacion.puertos.salida;

import co.edu.uco.jjskafee.catalogo.dominio.Categoria;
import co.edu.uco.jjskafee.catalogo.dominio.Producto;
import co.edu.uco.jjskafee.catalogo.dominio.TipoCategoria;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Lectura de las tablas del catálogo: categoria, producto y producto_adicion. */
public interface RepositorioCatalogo {

    Optional<Producto> buscarProducto(UUID idCafeteria, UUID idProducto);

    Set<UUID> adicionesPermitidas(UUID idCafeteria, UUID idProductoBase);

    Optional<Categoria> buscarCategoria(UUID idCafeteria, UUID idCategoria);

    /** Productos que se pueden vender ahora, con sus adiciones permitidas (para el punto de venta). */
    List<Producto> listarProductosVendibles(UUID idCafeteria);

    /** Categorías activas de un tipo, o de todos los tipos si tipo es null. */
    List<Categoria> listarCategoriasActivas(UUID idCafeteria, TipoCategoria tipo);
}
