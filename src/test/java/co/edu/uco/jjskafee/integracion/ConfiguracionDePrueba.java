package co.edu.uco.jjskafee.integracion;

import co.edu.uco.jjskafee.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.dominio.excepciones.NoAutenticadoException;
import co.edu.uco.jjskafee.servicios.output.ports.VerificadorDeToken;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import static co.edu.uco.jjskafee.DatosPrueba.*;

/** Reemplazos para las pruebas de integración: tokens de prueba y un reloj fijo. */
@TestConfiguration(proxyBeanMethods = false)
public class ConfiguracionDePrueba {

    /**
     * Día lejano en el futuro: los movimientos de las pruebas quedan en una fecha en la que
     * no hay datos creados a mano, así los totales no dependen de lo que tengas en tu BD.
     */
    public static final Instant AHORA_PRUEBA = Instant.parse("2099-01-15T15:00:00Z");
    public static final LocalDate DIA_PRUEBA = LocalDate.of(2099, 1, 15);   // en hora de Colombia

    public static final String TOKEN_ADMIN = "token-admin-a";
    public static final String TOKEN_CAJERO = "token-cajero-a";
    public static final String TOKEN_ADMIN_B = "token-admin-b";
    public static final String TOKEN_CAJERO_B = "token-cajero-b";
    public static final String TOKEN_CAJERO_INACTIVA = "token-cajero-c-inactiva";

    private static final Map<String, UsuarioAutenticado> USUARIOS = Map.of(
            TOKEN_ADMIN, ADMIN, TOKEN_CAJERO, CAJERO, TOKEN_ADMIN_B, ADMIN_B, TOKEN_CAJERO_B, CAJERO_B,
            TOKEN_CAJERO_INACTIVA, CAJERO_INACTIVA);

    @Bean
    VerificadorDeToken verificadorDePrueba() {
        return token -> {
            UsuarioAutenticado usuario = USUARIOS.get(token);
            if (usuario == null) {
                throw new NoAutenticadoException("El token no es válido o ya venció");
            }
            return usuario;
        };
    }

    @Bean
    @Primary
    Clock relojDePrueba() {
        return Clock.fixed(AHORA_PRUEBA, ZoneOffset.UTC);
    }
}
