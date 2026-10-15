package co.edu.uco.jjskafee.catalogo.aplicacion.puertos.entrada;

import co.edu.uco.jjskafee.catalogo.dominio.Categoria;
import co.edu.uco.jjskafee.catalogo.dominio.Producto;
import co.edu.uco.jjskafee.catalogo.dominio.TipoCategoria;
import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;

import java.util.List;

/** Puerto de entrada del catálogo para su propio controlador (solo lectura). */
public interface ConsultarCatalogo {

    List<Producto> productosVendibles(UsuarioAutenticado usuario);

    List<Categoria> categoriasActivas(UsuarioAutenticado usuario, TipoCategoria tipo);
}
