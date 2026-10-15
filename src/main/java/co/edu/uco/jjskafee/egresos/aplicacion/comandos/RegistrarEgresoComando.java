package co.edu.uco.jjskafee.egresos.aplicacion.comandos;

import java.math.BigDecimal;
import java.util.UUID;

/** Datos de un egreso. Sin cafetería, usuario ni fecha: los pone el servidor (ADR-0007). */
public record RegistrarEgresoComando(UUID idCategoria, BigDecimal monto, String descripcion, String comprobante) {
}
