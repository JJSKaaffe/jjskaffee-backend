package co.edu.uco.jjskafee.ingresos.infraestructura.salida;

import co.edu.uco.jjskafee.catalogo.api.CatalogoApi;
import co.edu.uco.jjskafee.ingresos.aplicacion.puertos.salida.CatalogoProductos;
import co.edu.uco.jjskafee.ingresos.dominio.Producto;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Puente entre módulos: ingresos → catálogo, SOLO a través de catalogo.api.
 * Traduce ProductoCatalogo (del catálogo) a Producto (del dominio de ingresos).
 */
public class AdaptadorCatalogoProductos implements CatalogoProductos {

    private final CatalogoApi catalogo;

    public AdaptadorCatalogoProductos(CatalogoApi catalogo) {
        this.catalogo = Objects.requireNonNull(catalogo);
    }

    @Override
    public Optional<Producto> buscar(UUID idCafeteria, UUID idProducto) {
        return catalogo.buscarProducto(idCafeteria, idProducto)
                .map(p -> new Producto(p.id(), p.nombre(), p.precio(), p.esAdicion(), p.disponible()));
    }

    @Override
    public Set<UUID> adicionesPermitidas(UUID idCafeteria, UUID idProductoBase) {
        return catalogo.adicionesPermitidas(idCafeteria, idProductoBase);
    }
}
