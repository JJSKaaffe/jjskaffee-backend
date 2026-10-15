package co.edu.uco.jjskafee.catalogo.dominio;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Producto del menú (RF22, RF23).
 * @param disponible       se puede vender: disponible = true y archivado = false en la BD
 * @param adicionesPermitidas ids de adiciones permitidas (vacío si es una adición)
 */
public record Producto(UUID id, UUID idCategoria, String nombre, BigDecimal precio, TipoProducto tipo,
                       boolean disponible, Set<UUID> adicionesPermitidas) {

    public Producto {
        Objects.requireNonNull(id);
        Objects.requireNonNull(nombre);
        Objects.requireNonNull(precio);
        Objects.requireNonNull(tipo);
        adicionesPermitidas = adicionesPermitidas == null ? Set.of() : Set.copyOf(adicionesPermitidas);
    }

    public boolean esAdicion() {
        return tipo == TipoProducto.ADICION;
    }
}
