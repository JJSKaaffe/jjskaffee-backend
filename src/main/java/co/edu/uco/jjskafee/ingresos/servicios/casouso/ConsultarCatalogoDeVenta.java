package co.edu.uco.jjskafee.ingresos.servicios.casouso;

import co.edu.uco.jjskafee.compartido.dominio.Categoria;
import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.compartido.dominio.enums.Permiso;
import co.edu.uco.jjskafee.compartido.servicios.output.ports.UnidadDeTrabajo;
import co.edu.uco.jjskafee.ingresos.servicios.input.dto.response.CatalogoDeVentaResponse;
import co.edu.uco.jjskafee.ingresos.servicios.input.ports.ConsultarCatalogoDeVentaUseCase;
import co.edu.uco.jjskafee.ingresos.servicios.output.ports.CatalogoDeVentaRepository;

import java.util.List;
import java.util.Objects;

/**
 * Caso de uso: catálogo de la pantalla de venta. Productos con adiciones y precios para quien puede
 * vender; las categorías de ingreso solo para quien puede registrar ingresos libres (el administrador).
 */
public class ConsultarCatalogoDeVenta implements ConsultarCatalogoDeVentaUseCase {

    private final CatalogoDeVentaRepository catalogo;
    private final UnidadDeTrabajo unidadDeTrabajo;

    public ConsultarCatalogoDeVenta(CatalogoDeVentaRepository catalogo, UnidadDeTrabajo unidadDeTrabajo) {
        this.catalogo = Objects.requireNonNull(catalogo);
        this.unidadDeTrabajo = Objects.requireNonNull(unidadDeTrabajo);
    }

    @Override
    public CatalogoDeVentaResponse consultar(UsuarioAutenticado usuario) {
        boolean veCategorias = usuario.tienePermiso(Permiso.REGISTRAR_INGRESO_LIBRE);
        return unidadDeTrabajo.ejecutar(usuario.idCafeteria(), () -> {
            List<Categoria> categorias = veCategorias ? catalogo.categoriasDeIngresoActivas() : List.of();
            return CatalogoDeVentaResponse.desde(catalogo.productosParaVender(), categorias);
        });
    }
}
