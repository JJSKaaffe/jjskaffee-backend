package co.edu.uco.jjskafee.ingresos.servicios.input.dto.response;

import co.edu.uco.jjskafee.compartido.dominio.Categoria;
import co.edu.uco.jjskafee.compartido.dominio.Producto;
import co.edu.uco.jjskafee.ingresos.dominio.ProductoDelMenu;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta de GET /api/ingresos/catalogo: lo que la pantalla de venta muestra. Los precios son
 * informativos; al registrar, el total lo vuelve a calcular el servidor con los precios del catálogo.
 */
public record CatalogoDeVentaResponse(List<ProductoResponse> productos, List<CategoriaResponse> categoriasIngreso) {

    public record ProductoResponse(UUID idProducto, String nombre, BigDecimal precio, List<AdicionResponse> adiciones) {
    }

    public record AdicionResponse(UUID idProducto, String nombre, BigDecimal precio) {
    }

    public record CategoriaResponse(UUID idCategoria, String nombre) {
    }

    public static CatalogoDeVentaResponse desde(List<ProductoDelMenu> productos, List<Categoria> categoriasIngreso) {
        return new CatalogoDeVentaResponse(
                productos.stream().map(CatalogoDeVentaResponse::producto).toList(),
                categoriasIngreso.stream().map(c -> new CategoriaResponse(c.getId(), c.getNombre())).toList());
    }

    private static ProductoResponse producto(ProductoDelMenu p) {
        Producto base = p.base();
        return new ProductoResponse(base.getId(), base.getNombre(), base.getPrecio(),
                p.adiciones().stream().map(a -> new AdicionResponse(a.getId(), a.getNombre(), a.getPrecio())).toList());
    }
}
