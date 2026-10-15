package co.edu.uco.jjskafee.catalogo.api;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lo ÚNICO que los demás módulos pueden usar del catálogo.
 * Devuelve copias (records), nunca las clases internas del módulo.
 * Si se llama dentro de una transacción abierta (por ejemplo, al registrar una venta), se une a ella.
 */
public interface CatalogoApi {

    Optional<ProductoCatalogo> buscarProducto(UUID idCafeteria, UUID idProducto);

    /** Ids de las adiciones que se le pueden poner a un producto base (tabla producto_adicion). */
    Set<UUID> adicionesPermitidas(UUID idCafeteria, UUID idProductoBase);

    Optional<CategoriaCatalogo> buscarCategoria(UUID idCafeteria, UUID idCategoria);
}
