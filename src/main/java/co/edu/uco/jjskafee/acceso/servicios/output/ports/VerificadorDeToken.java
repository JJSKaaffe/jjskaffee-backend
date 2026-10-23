package co.edu.uco.jjskafee.acceso.servicios.output.ports;

import co.edu.uco.jjskafee.acceso.dominio.Identidad;

/**
 * Puerto de salida: verifica el token del proveedor de identidad y devuelve quién es la persona.
 * No sabe de cafeterías ni de roles (ADR-0016). Si el token no sirve lanza NoAutenticadoException.
 */
public interface VerificadorDeToken {

    Identidad verificar(String token);
}
