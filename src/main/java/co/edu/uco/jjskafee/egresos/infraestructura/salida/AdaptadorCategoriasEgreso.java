package co.edu.uco.jjskafee.egresos.infraestructura.salida;

import co.edu.uco.jjskafee.catalogo.api.CatalogoApi;
import co.edu.uco.jjskafee.egresos.aplicacion.puertos.salida.CategoriasEgreso;
import co.edu.uco.jjskafee.egresos.dominio.CategoriaEgreso;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Puente entre módulos: egresos → catálogo, SOLO a través de catalogo.api. */
public class AdaptadorCategoriasEgreso implements CategoriasEgreso {

    private static final String TIPO_EGRESO = "EGRESO";

    private final CatalogoApi catalogo;

    public AdaptadorCategoriasEgreso(CatalogoApi catalogo) {
        this.catalogo = Objects.requireNonNull(catalogo);
    }

    @Override
    public Optional<CategoriaEgreso> buscar(UUID idCafeteria, UUID idCategoria) {
        return catalogo.buscarCategoria(idCafeteria, idCategoria)
                .map(c -> new CategoriaEgreso(c.id(), c.nombre(), TIPO_EGRESO.equals(c.tipo()), c.archivada()));
    }
}
