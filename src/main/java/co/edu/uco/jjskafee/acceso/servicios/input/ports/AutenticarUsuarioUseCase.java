package co.edu.uco.jjskafee.acceso.servicios.input.ports;

import co.edu.uco.jjskafee.acceso.dominio.Identidad;
import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;

import java.util.UUID;

/** Puerto de entrada: quién hace la petición y con qué rol, en la cafetería que eligió (ADR-0016). */
public interface AutenticarUsuarioUseCase {

    /** Verifica el token. Sin token válido lanza NoAutenticadoException (401). */
    Identidad identificar(String token);

    /**
     * Arma el usuario con su rol en esa cafetería. Si la persona no tiene una membresía activa
     * en ella (o la cafetería no existe) lanza SinPermisoException (403).
     */
    UsuarioAutenticado autorizarEnCafeteria(Identidad identidad, UUID idCafeteria);
}
