package co.edu.uco.jjskafee.ingresos.aplicacion.comandos;

import java.util.List;
import java.util.UUID;

/**
 * Lo que se necesita para registrar una venta: solo ids y cantidades.
 * Sin precios, total, cafetería, usuario ni fecha: eso lo pone el servidor.
 */
public record RegistrarVentaComando(List<Linea> lineas) {

    public RegistrarVentaComando {
        lineas = lineas == null ? List.of() : List.copyOf(lineas);
    }

    /** Un producto base con sus adiciones. */
    public record Linea(UUID idProducto, int cantidad, List<Adicion> adiciones) {
        public Linea {
            adiciones = adiciones == null ? List.of() : List.copyOf(adiciones);
        }
    }

    /** Una adición; si no se indica cantidad, se toma 1. */
    public record Adicion(UUID idProducto, int cantidad) {
    }
}
