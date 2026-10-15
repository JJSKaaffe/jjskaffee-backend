package co.edu.uco.jjskafee.ingresos.dominio;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Una línea de la venta (tabla detalle_venta). Si es una adición, apunta a la línea del
 * producto base (id_detalle_base). El precio se copia al vender (IMP18-03): un cambio de
 * precio posterior no altera ventas pasadas.
 */
public final class DetalleVenta {

    private final UUID id;
    private final Producto producto;
    private final int cantidad;
    private final BigDecimal precioUnitario;
    private final DetalleVenta detalleBase;   // null cuando la línea es un producto base

    DetalleVenta(UUID id, Producto producto, int cantidad, BigDecimal precioUnitario, DetalleVenta detalleBase) {
        this.id = Objects.requireNonNull(id);
        this.producto = Objects.requireNonNull(producto);
        this.cantidad = cantidad;
        this.precioUnitario = Objects.requireNonNull(precioUnitario);
        this.detalleBase = detalleBase;
    }

    /** Igual que la columna generada subtotal = cantidad * precio_unitario. */
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
