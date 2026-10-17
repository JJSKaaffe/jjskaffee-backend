package co.edu.uco.jjskafee.dominio;

import co.edu.uco.jjskafee.dominio.enums.TipoProducto;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Producto del menú: base (café, pan) o adición (leche de almendra, shot extra). */
public class Producto {

    private final UUID id;
    private final String nombre;
    private final BigDecimal precio;
    private final TipoProducto tipo;
    private final boolean disponible;

    public Producto(UUID id, String nombre, BigDecimal precio, TipoProducto tipo, boolean disponible) {
        this.id = Objects.requireNonNull(id, "El id del producto es obligatorio");
        this.nombre = Objects.requireNonNull(nombre, "El nombre del producto es obligatorio");
        this.precio = Objects.requireNonNull(precio, "El precio del producto es obligatorio");
        this.tipo = Objects.requireNonNull(tipo, "El tipo del producto es obligatorio");
        this.disponible = disponible;
    }

    public boolean esBase() { return tipo == TipoProducto.BASE; }
    public boolean esAdicion() { return tipo == TipoProducto.ADICION; }

    public UUID getId() { return id; }
    public String getNombre() { return nombre; }
    public BigDecimal getPrecio() { return precio; }
    public TipoProducto getTipo() { return tipo; }
    public boolean isDisponible() { return disponible; }

    // Dos productos son iguales si tienen el mismo id (sirve para usarlos como clave en un Map)
    @Override
    public boolean equals(Object o) {
        return o instanceof Producto otro && id.equals(otro.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }
}
