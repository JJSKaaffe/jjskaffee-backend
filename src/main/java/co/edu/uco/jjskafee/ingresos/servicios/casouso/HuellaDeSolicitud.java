package co.edu.uco.jjskafee.ingresos.servicios.casouso;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Huella SHA-256 (64 caracteres hexadecimales, columna hash_solicitud) de lo que se pidió. Con la misma
 * Idempotency-Key, una huella distinta significa que la clave se está reutilizando para otro registro.
 */
final class HuellaDeSolicitud {

    private HuellaDeSolicitud() { }

    static String sha256(String texto) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);   // toda JVM lo trae
        }
    }
}
