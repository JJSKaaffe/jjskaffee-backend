package co.edu.uco.jjskafee.egresos.infraestructura.configuracion;

import co.edu.uco.jjskafee.compartido.servicios.output.ports.CatalogoRepository;
import co.edu.uco.jjskafee.compartido.servicios.output.ports.UnidadDeTrabajo;
import co.edu.uco.jjskafee.egresos.infraestructura.output.repositorios.estructurados.PgEgresoRepository;
import co.edu.uco.jjskafee.egresos.servicios.casouso.RegistrarEgreso;
import co.edu.uco.jjskafee.egresos.servicios.input.ports.RegistrarEgresoUseCase;
import co.edu.uco.jjskafee.egresos.servicios.output.ports.EgresoRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;

/** Arma el módulo de egresos: crea su adaptador de salida y el caso de uso (Java puro) y los conecta. */
@Configuration
public class EgresosConfig {

    @Bean
    public EgresoRepository egresoRepository(JdbcTemplate jdbc) {
        return new PgEgresoRepository(jdbc);
    }

    @Bean
    public RegistrarEgresoUseCase registrarEgreso(CatalogoRepository catalogo, EgresoRepository egresos,
                                                  UnidadDeTrabajo unidadDeTrabajo, Clock reloj) {
        return new RegistrarEgreso(catalogo, egresos, unidadDeTrabajo, reloj);
    }
}
