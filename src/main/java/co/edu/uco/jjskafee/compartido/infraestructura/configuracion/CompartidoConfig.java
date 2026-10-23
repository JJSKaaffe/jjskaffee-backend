package co.edu.uco.jjskafee.compartido.infraestructura.configuracion;

import co.edu.uco.jjskafee.compartido.infraestructura.output.repositorios.estructurados.PgCatalogoRepository;
import co.edu.uco.jjskafee.compartido.infraestructura.output.repositorios.estructurados.PgUnidadDeTrabajo;
import co.edu.uco.jjskafee.compartido.servicios.output.ports.CatalogoRepository;
import co.edu.uco.jjskafee.compartido.servicios.output.ports.UnidadDeTrabajo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * Arma el kernel compartido: reloj, unidad de trabajo (transacción + RLS) y catálogo.
 * Cada módulo tiene su propia configuración que conecta sus casos de uso con estos puertos.
 */
@Configuration
public class CompartidoConfig {

    /** Reloj del servidor: la fecha y hora de cada movimiento la pone el backend (ADR-0007, RF43). */
    @Bean
    public Clock reloj() {
        return Clock.systemUTC();
    }

    @Bean
    public UnidadDeTrabajo unidadDeTrabajo(PlatformTransactionManager transacciones, JdbcTemplate jdbc) {
        return new PgUnidadDeTrabajo(new TransactionTemplate(transacciones), jdbc);
    }

    @Bean
    public CatalogoRepository catalogoRepository(JdbcTemplate jdbc) {
        return new PgCatalogoRepository(jdbc);
    }
}
