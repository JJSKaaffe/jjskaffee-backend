package co.edu.uco.jjskafee.catalogo.api;

import java.util.UUID;

/**
 * Categoría tal como la ven otros módulos.
 * @param tipo "INGRESO", "EGRESO" o "MENU" (columna categoria.tipo)
 */
public record CategoriaCatalogo(UUID id, String nombre, String tipo, boolean archivada) {
}
