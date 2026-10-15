package co.edu.uco.jjskafee.ingresos.aplicacion.puertos.salida;

import co.edu.uco.jjskafee.ingresos.dominio.Producto;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Lo que ingresos necesita del catálogo. Lo implementa un adaptador que usa CatalogoApi. */
public interface CatalogoProductos {

    Optional<Producto> buscar(UUID idCafeteria, UUID idProducto);

    Set<UUID> adicionesPermitidas(UUID idCafeteria, UUID idProductoBase);
}
