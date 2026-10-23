package co.edu.uco.jjskafee.ingresos.servicios.input.ports;

import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.ingresos.servicios.input.dto.response.CatalogoDeVentaResponse;

/** Puerto de entrada: el catálogo que necesita la pantalla de venta de la cafetería elegida. */
public interface ConsultarCatalogoDeVentaUseCase {

    CatalogoDeVentaResponse consultar(UsuarioAutenticado usuario);
}
