package co.edu.uco.jjskafee.ingresos.dominio;

import co.edu.uco.jjskafee.compartido.dominio.Producto;

import java.util.List;
import java.util.Objects;

/**
 * Un producto que se puede vender en la pantalla de venta, con las adiciones que se le pueden poner
 * (tabla producto_adicion). Solo entra lo que Venta.agregar aceptaría: un producto base disponible con
 * precio mayor que 0 y adiciones disponibles con precio mayor que 0.
 */
public record ProductoDelMenu(Producto base, List<Producto> adiciones) {

    public ProductoDelMenu {
        Objects.requireNonNull(base, "El producto base es obligatorio");
        if (!sePuedeVender(base) || !base.esBase()) {
            throw new IllegalArgumentException(base.getNombre() + " no es un producto base que se pueda vender");
        }
        adiciones = adiciones == null ? List.of() : List.copyOf(adiciones);
        for (Producto adicion : adiciones) {
            if (!sePuedeVender(adicion) || !adicion.esAdicion()) {
                throw new IllegalArgumentException(adicion.getNombre() + " no es una adición que se pueda vender");
            }
        }
    }

    /** Disponible y con precio mayor que 0, como lo exige Venta al registrar. */
    public static boolean sePuedeVender(Producto producto) {
        return producto.isDisponible() && producto.getPrecio().signum() > 0;
    }
}
