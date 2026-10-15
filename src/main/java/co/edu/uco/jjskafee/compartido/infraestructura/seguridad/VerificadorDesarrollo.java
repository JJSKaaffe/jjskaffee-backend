package co.edu.uco.jjskafee.compartido.infraestructura.seguridad;

import co.edu.uco.jjskafee.compartido.aplicacion.IdentidadVerificada;
import co.edu.uco.jjskafee.compartido.aplicacion.puertos.VerificadorDeToken;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.NoAutenticadoException;
import co.edu.uco.jjskafee.compartido.dominio.mensajes.MensajesCompartidos;

import java.util.UUID;

/**
 * SOLO PARA PROBAR EN TU PC sin Firebase (perfil "local" + jjskaffee.seguridad.modo=desarrollo).
 * El "token" tiene la forma  dev:<id_cafeteria>:<firebase_uid>, por ejemplo:
 *   Authorization: Bearer dev:11111111-1111-1111-1111-111111111111:firebase-cajero-demo
 * Aun así el usuario debe existir y estar activo en la tabla usuario.
 * Nunca se activa fuera del perfil local (SeguridadConfiguracion).
 */
public class VerificadorDesarrollo implements VerificadorDeToken {

    private static final String PREFIJO = "dev:";

    @Override
    public IdentidadVerificada verificar(String token) {
        if (token == null || !token.startsWith(PREFIJO)) {
            throw new NoAutenticadoException(MensajesCompartidos.TOKEN_INVALIDO);
        }
        String[] partes = token.substring(PREFIJO.length()).split(":", 2);
        if (partes.length != 2 || partes[1].isBlank()) {
            throw new NoAutenticadoException(MensajesCompartidos.TOKEN_INVALIDO);
        }
        try {
            return new IdentidadVerificada(partes[1], UUID.fromString(partes[0]));
        } catch (IllegalArgumentException e) {
            throw new NoAutenticadoException(MensajesCompartidos.TOKEN_SIN_CAFETERIA);
        }
    }
}
