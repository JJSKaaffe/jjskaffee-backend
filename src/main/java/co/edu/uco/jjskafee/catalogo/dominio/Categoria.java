package co.edu.uco.jjskafee.catalogo.dominio;

import java.util.Objects;
import java.util.UUID;

/** Categoría propia de cada cafetería (RF18, ADR-0003). Se archiva, no se borra. */
public record Categoria(UUID id, String nombre, TipoCategoria tipo, boolean archivada) {

    public Categoria {
        Objects.requireNonNull(id);
        Objects.requireNonNull(nombre);
        Objects.requireNonNull(tipo);
    }
}
