package co.edu.uco.jjskafee.acceso.servicios.output.ports;

import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;

/** Puerto de salida: verifica el token de inicio de sesión (lo implementa el adaptador de Firebase). */
public interface VerificadorDeToken {

    UsuarioAutenticado verificar(String token);
}
