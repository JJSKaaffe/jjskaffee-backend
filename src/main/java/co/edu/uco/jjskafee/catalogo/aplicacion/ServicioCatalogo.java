package co.edu.uco.jjskafee.catalogo.aplicacion;

import co.edu.uco.jjskafee.catalogo.api.CatalogoApi;
import co.edu.uco.jjskafee.catalogo.api.CategoriaCatalogo;
import co.edu.uco.jjskafee.catalogo.api.ProductoCatalogo;
import co.edu.uco.jjskafee.catalogo.aplicacion.puertos.entrada.ConsultarCatalogo;
import co.edu.uco.jjskafee.catalogo.aplicacion.puertos.salida.RepositorioCatalogo;
import co.edu.uco.jjskafee.catalogo.dominio.Categoria;
import co.edu.uco.jjskafee.catalogo.dominio.Producto;
import co.edu.uco.jjskafee.catalogo.dominio.TipoCategoria;
import co.edu.uco.jjskafee.compartido.aplicacion.puertos.UnidadDeTrabajo;
import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Implementa la API pública del catálogo (para otros módulos) y la consulta para su controlador.
 * Java puro: lo crea CatalogoConfiguracion. Toda lectura pasa por la UnidadDeTrabajo (RLS activo).
 */
public class ServicioCatalogo implements CatalogoApi, ConsultarCatalogo {

    private final RepositorioCatalogo repositorio;
    private final UnidadDeTrabajo unidadDeTrabajo;

    public ServicioCatalogo(RepositorioCatalogo repositorio, UnidadDeTrabajo unidadDeTrabajo) {
        this.repositorio = Objects.requireNonNull(repositorio);
        this.unidadDeTrabajo = Objects.requireNonNull(unidadDeTrabajo);
    }

    // ---------- API pública (otros módulos) ----------

    @Override
    public Optional<ProductoCatalogo> buscarProducto(UUID idCafeteria, UUID idProducto) {
        return unidadDeTrabajo.ejecutar(idCafeteria, () -> repositorio.buscarProducto(idCafeteria, idProducto))
                .map(p -> new ProductoCatalogo(p.id(), p.nombre(), p.precio(), p.esAdicion(), p.disponible()));
    }

    @Override
    public Set<UUID> adicionesPermitidas(UUID idCafeteria, UUID idProductoBase) {
        return unidadDeTrabajo.ejecutar(idCafeteria, () -> repositorio.adicionesPermitidas(idCafeteria, idProductoBase));
    }

    @Override
    public Optional<CategoriaCatalogo> buscarCategoria(UUID idCafeteria, UUID idCategoria) {
        return unidadDeTrabajo.ejecutar(idCafeteria, () -> repositorio.buscarCategoria(idCafeteria, idCategoria))
                .map(c -> new CategoriaCatalogo(c.id(), c.nombre(), c.tipo().name(), c.archivada()));
    }

    // ---------- Consulta para el controlador del catálogo ----------

    @Override
    public List<Producto> productosVendibles(UsuarioAutenticado usuario) {
        return unidadDeTrabajo.ejecutar(usuario.idCafeteria(),
                () -> repositorio.listarProductosVendibles(usuario.idCafeteria()));
    }

    @Override
    public List<Categoria> categoriasActivas(UsuarioAutenticado usuario, TipoCategoria tipo) {
        return unidadDeTrabajo.ejecutar(usuario.idCafeteria(),
                () -> repositorio.listarCategoriasActivas(usuario.idCafeteria(), tipo));
    }
}
