package co.edu.uco.jjskafee.integracion;

import co.edu.uco.jjskafee.acceso.dominio.Identidad;
import co.edu.uco.jjskafee.acceso.servicios.output.ports.VerificadorDeToken;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.NoAutenticadoException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static co.edu.uco.jjskafee.DatosPrueba.*;

/**
 * Reemplazos para las pruebas de integración: tokens de prueba y un reloj fijo.
 * Cada token solo dice quién es la persona (uid), como Firebase; la cafetería va en X-Cafeteria-Id
 * y el rol sale de la membresía real en la BD (seeds db/04, db/06 y db/08).
 */
@TestConfiguration(proxyBeanMethods = false)
public class ConfiguracionDePrueba {

    /**
     * Día lejano en el futuro: los movimientos de las pruebas quedan en una fecha en la que
     * no hay datos creados a mano, así los totales no dependen de lo que tengas en tu BD.
     */
    public static final Instant AHORA_PRUEBA = Instant.parse("2099-01-15T15:00:00Z");
    public static final LocalDate DIA_PRUEBA = LocalDate.of(2099, 1, 15);   // en hora de Colombia

    /** Laura: administradora en A y cajera en B. */
    public static final String TOKEN_ADMIN = "token-admin-a";
    public static final String TOKEN_CAJERO = "token-cajero-a";
    public static final String TOKEN_ADMIN_B = "token-admin-b";
    public static final String TOKEN_CAJERO_B = "token-cajero-b";
    public static final String TOKEN_CAJERO_INACTIVA = "token-cajero-c-inactiva";
    /** Sesión válida en Firebase, pero sin membresía en ninguna cafetería. */
    public static final String TOKEN_SIN_MEMBRESIA = "token-sin-cafeteria";

    private static final Map<String, String> UIDS = Map.of(
            TOKEN_ADMIN, UID_ADMIN, TOKEN_CAJERO, UID_CAJERO, TOKEN_ADMIN_B, UID_ADMIN_B, TOKEN_CAJERO_B, UID_CAJERO_B,
            TOKEN_CAJERO_INACTIVA, UID_CAJERO_INACTIVA, TOKEN_SIN_MEMBRESIA, UID_SIN_MEMBRESIA);

    /** Cafetería que cada persona elige por defecto en las pruebas (Laura elige A). */
    private static final Map<String, UUID> CAFETERIA_POR_DEFECTO = Map.of(
            TOKEN_ADMIN, CAFETERIA, TOKEN_CAJERO, CAFETERIA, TOKEN_ADMIN_B, CAFETERIA_B, TOKEN_CAJERO_B, CAFETERIA_B,
            TOKEN_CAJERO_INACTIVA, CAFETERIA_INACTIVA, TOKEN_SIN_MEMBRESIA, CAFETERIA);

    /** Encabezados de una petición: token y cafetería elegida. */
    public static HttpHeaders sesion(String token, UUID idCafeteria) {
        HttpHeaders encabezados = new HttpHeaders();
        encabezados.setBearerAuth(token);
        encabezados.set("X-Cafeteria-Id", idCafeteria.toString());
        return encabezados;
    }

    /** Encabezados con la cafetería por defecto de ese token. */
    public static HttpHeaders sesion(String token) {
        return sesion(token, CAFETERIA_POR_DEFECTO.get(token));
    }

    @Bean
    VerificadorDeToken verificadorDePrueba() {
        return token -> {
            String uid = UIDS.get(token);
            if (uid == null) {
                throw new NoAutenticadoException("El token no es válido o ya venció");
            }
            return new Identidad(uid);
        };
    }

    @Bean
    @Primary
    Clock relojDePrueba() {
        return Clock.fixed(AHORA_PRUEBA, ZoneOffset.UTC);
    }
}
