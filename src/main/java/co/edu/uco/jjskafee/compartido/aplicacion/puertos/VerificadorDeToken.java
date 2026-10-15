package co.edu.uco.jjskafee.compartido.aplicacion.puertos;

import co.edu.uco.jjskafee.compartido.aplicacion.IdentidadVerificada;

/**
 * Verifica el token de inicio de sesión. Lo implementa el adaptador de Firebase
 * (la autenticación es un módulo externo · ADR-0001); este backend no tiene login propio.
 */
public interface VerificadorDeToken {

    /** @throws co.edu.uco.jjskafee.compartido.dominio.excepciones.NoAutenticadoException si no es válido */
    IdentidadVerificada verificar(String token);
}
