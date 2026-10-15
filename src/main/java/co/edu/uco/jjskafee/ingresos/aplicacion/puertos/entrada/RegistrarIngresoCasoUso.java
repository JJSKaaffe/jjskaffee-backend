package co.edu.uco.jjskafee.ingresos.aplicacion.puertos.entrada;

import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.ingresos.aplicacion.comandos.RegistrarVentaComando;
import co.edu.uco.jjskafee.ingresos.dominio.Venta;

import java.util.UUID;

/** Puerto de entrada: registrar una venta (RF24–RF27) y deshacerla en 60 s (ADR-0008). */
public interface RegistrarIngresoCasoUso {

    Venta registrar(RegistrarVentaComando comando, UsuarioAutenticado usuario);

    Venta deshacer(UUID idVenta, UsuarioAutenticado usuario);
}
