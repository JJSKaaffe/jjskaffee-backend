package co.edu.uco.jjskafee.egresos.aplicacion.puertos.salida;

import co.edu.uco.jjskafee.egresos.dominio.CategoriaEgreso;

import java.util.Optional;
import java.util.UUID;

/** Lo que egresos necesita del catálogo. Lo implementa un adaptador que usa CatalogoApi. */
public interface CategoriasEgreso {

    Optional<CategoriaEgreso> buscar(UUID idCafeteria, UUID idCategoria);
}
