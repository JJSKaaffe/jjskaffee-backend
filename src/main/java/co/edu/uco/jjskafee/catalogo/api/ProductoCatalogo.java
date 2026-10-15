package co.edu.uco.jjskafee.catalogo.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Producto tal como lo ven otros módulos.
 * @param esAdicion  true si es una adición (leche de almendra, shot extra); false si es un producto base
 * @param disponible true si se puede vender ahora (disponible y no archivado)
 */
public record ProductoCatalogo(UUID id, String nombre, BigDecimal precio, boolean esAdicion, boolean disponible) {
}
