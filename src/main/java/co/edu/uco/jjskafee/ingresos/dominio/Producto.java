package co.edu.uco.jjskafee.ingresos.dominio;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Lo que el módulo ingresos necesita saber de un producto para venderlo.
 * Se arma a partir del catálogo (CatalogoApi), así ingresos no depende de las clases internas del catálogo.
 */
public record Producto(UUID id, String nombre, BigDecimal precio, boolean esAdicion, boolean disponible) {

    public Producto {
        Objects.requireNonNull(id);
        Objects.requireNonNull(nombre);
        Objects.requireNonNull(precio);
    }

    public boolean esBase() {
        return !esAdicion;
    }
}
