package co.edu.uco.jjskafee.catalogo.infraestructura.web;

import co.edu.uco.jjskafee.catalogo.aplicacion.puertos.entrada.ConsultarCatalogo;
import co.edu.uco.jjskafee.catalogo.dominio.TipoCategoria;
import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * Lectura del catálogo para armar las pantallas de venta y de egreso.
 * Crear o editar productos/categorías es de "gestión de menú" (otra funcionalidad).
 */
@RestController
@RequestMapping("/api/catalogo")
public class CatalogoControlador {

    private final ConsultarCatalogo consultarCatalogo;

    public CatalogoControlador(ConsultarCatalogo consultarCatalogo) {
        this.consultarCatalogo = Objects.requireNonNull(consultarCatalogo);
    }

    /** GET /api/catalogo/productos → productos vendibles con las adiciones que admite cada base. */
    @GetMapping("/productos")
    public List<ProductoResponse> productos(UsuarioAutenticado usuario) {
        return consultarCatalogo.productosVendibles(usuario).stream().map(ProductoResponse::desde).toList();
    }

    /** GET /api/catalogo/categorias?tipo=EGRESO → categorías activas (tipo opcional). */
    @GetMapping("/categorias")
    public List<CategoriaResponse> categorias(@RequestParam(required = false) TipoCategoria tipo,
                                              UsuarioAutenticado usuario) {
        return consultarCatalogo.categoriasActivas(usuario, tipo).stream().map(CategoriaResponse::desde).toList();
    }
}
