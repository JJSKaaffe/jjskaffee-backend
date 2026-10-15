package co.edu.uco.jjskafee.egresos.dominio;

import java.util.Objects;
import java.util.UUID;

/**
 * Lo que egresos necesita saber de una categoría (se arma desde CatalogoApi).
 * @param esDeEgreso true si categoria.tipo = 'EGRESO'
 */
public record CategoriaEgreso(UUID id, String nombre, boolean esDeEgreso, boolean archivada) {

    public CategoriaEgreso {
        Objects.requireNonNull(id);
        Objects.requireNonNull(nombre);
    }

    /** RF18: un egreso solo va en una categoría de egreso que no esté archivada. */
    public boolean esEgresoActiva() {
        return esDeEgreso && !archivada;
    }
}
