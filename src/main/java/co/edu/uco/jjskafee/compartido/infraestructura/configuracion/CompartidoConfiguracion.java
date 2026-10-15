package co.edu.uco.jjskafee.compartido.infraestructura.configuracion;

import co.edu.uco.jjskafee.compartido.aplicacion.AutenticarUsuario;
import co.edu.uco.jjskafee.compartido.aplicacion.puertos.ProveedorMensajes;
import co.edu.uco.jjskafee.compartido.aplicacion.puertos.RepositorioUsuarios;
import co.edu.uco.jjskafee.compartido.aplicacion.puertos.UnidadDeTrabajo;
import co.edu.uco.jjskafee.compartido.aplicacion.puertos.VerificadorDeToken;
import co.edu.uco.jjskafee.compartido.infraestructura.mensajes.ProveedorMensajesSpring;
import co.edu.uco.jjskafee.compartido.infraestructura.persistencia.PgRepositorioUsuarios;
import co.edu.uco.jjskafee.compartido.infraestructura.persistencia.PgUnidadDeTrabajo;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/** Beans del módulo compartido. Los casos de uso son Java puro y se crean aquí, no con anotaciones. */
@Configuration(proxyBeanMethods = false)
public class CompartidoConfiguracion {

    /** Reloj en UTC-0: todas las fechas que pone el servidor salen de aquí (ADR-0007). */
    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }

    @Bean
    UnidadDeTrabajo unidadDeTrabajo(PlatformTransactionManager transacciones, JdbcTemplate jdbc) {
        return new PgUnidadDeTrabajo(new TransactionTemplate(transacciones), jdbc);
    }

    @Bean
    RepositorioUsuarios repositorioUsuarios(JdbcTemplate jdbc) {
        return new PgRepositorioUsuarios(jdbc);
    }

    @Bean
    ProveedorMensajes proveedorMensajes(MessageSource mensajes) {
        return new ProveedorMensajesSpring(mensajes);
    }

    @Bean
    AutenticarUsuario autenticarUsuario(VerificadorDeToken verificador, RepositorioUsuarios usuarios,
                                        UnidadDeTrabajo unidadDeTrabajo) {
        return new AutenticarUsuario(verificador, usuarios, unidadDeTrabajo);
    }
}
