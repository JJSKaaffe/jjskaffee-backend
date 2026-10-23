package co.edu.uco.jjskafee.acceso.dominio;

/**
 * Persona que inició sesión, tal como la identifica Firebase Authentication (uid). El token solo dice
 * quién es; en qué cafetería trabaja y con qué rol sale de sus membresías (tabla usuarios, ADR-0016).
 */
public record Identidad(String uid) {

    public Identidad {
        if (uid == null || uid.isBlank()) {
            throw new IllegalArgumentException("El uid de la identidad es obligatorio");
        }
    }
}
