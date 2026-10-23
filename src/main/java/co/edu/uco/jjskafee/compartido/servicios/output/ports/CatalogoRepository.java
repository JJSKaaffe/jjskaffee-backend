package co.edu.uco.jjskafee.compartido.servicios.output.ports;

import co.edu.uco.jjskafee.compartido.dominio.Categoria;
import co.edu.uco.jjskafee.compartido.dominio.Producto;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Puerto de salida: consultar productos, adiciones y categorías. */
public interface CatalogoRepository {

    Optional<Producto> buscarProducto(UUID idProducto);

    /** Ids de las adiciones que se le pueden poner a un producto base (tabla producto_adicion). */
    Set<UUID> adicionesPermitidas(UUID idProductoBase);

    Optional<Categoria> buscarCategoria(UUID idCategoria);
}
