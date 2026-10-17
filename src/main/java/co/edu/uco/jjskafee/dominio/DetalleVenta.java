package co.edu.uco.jjskafee.dominio;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Una línea de la venta. Si es una adición, apunta al detalle del producto
 * base al que se le agregó (detalleBase). El precio se copia al momento de
 * vender, para que un cambio de precio posterior no altere ventas pasadas.
 */
public class DetalleVenta {

    private final UUID id;
    private final Producto producto;
    private final int cantidad;
    private final BigDecimal precioUnitario;
    private final DetalleVenta detalleBase;   // null cuando el detalle es un producto base

    public DetalleVenta(UUID id, Producto producto, int cantidad, BigDecimal precioUnitario, DetalleVenta detalleBase) {
        this.id = Objects.requireNonNull(id);
        this.producto = Objects.requireNonNull(producto);
        this.cantidad = cantidad;
        this.precioUnitario = Objects.requireNonNull(precioUnitario);
        this.detalleBase = detalleBase;
    }

    public BigDecimal subtotal() {
        return precioUnitario.multiply(BigDecimal.valueOf(cantidad));
    }

    public boolean esAdicion() {
        return detalleBase != null;
    }

    public UUID getId() { return id; }
    public Producto getProducto() { return producto; }
    public int getCantidad() { return cantidad; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public DetalleVenta getDetalleBase() { return detalleBase; }
}
