package co.edu.uco.jjskafee.catalogo.infraestructura.web;

import co.edu.uco.jjskafee.catalogo.dominio.Producto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductoResponse(UUID idProducto, UUID idCategoria, String nombre, BigDecimal precio, String tipo,
                               List<UUID> adicionesPermitidas) {

    public static ProductoResponse desde(Producto p) {
        return new ProductoResponse(p.id(), p.idCategoria(), p.nombre(), p.precio(), p.tipo().name(),
                p.adicionesPermitidas().stream().sorted().toList());
    }
}
