package co.edu.uco.jjskafee.ingresos.servicios.output.ports;

import co.edu.uco.jjskafee.compartido.dominio.Categoria;
import co.edu.uco.jjskafee.ingresos.dominio.ProductoDelMenu;

import java.util.List;

/**
 * Puerto de salida: lo que la pantalla de venta necesita del catálogo de la cafetería activa
 * (siempre dentro de UnidadDeTrabajo). Todo en pocas consultas, sin una por producto.
 */
public interface CatalogoDeVentaRepository {

    /** Productos base que se pueden vender, cada uno con sus adiciones permitidas que se pueden vender. */
    List<ProductoDelMenu> productosParaVender();

    /** Categorías de tipo INGRESO no archivadas, para los ingresos libres. */
    List<Categoria> categoriasDeIngresoActivas();
}
