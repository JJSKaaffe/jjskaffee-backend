package co.edu.uco.jjskafee.movimientos.infraestructura.configuracion;

import co.edu.uco.jjskafee.compartido.servicios.output.ports.UnidadDeTrabajo;
import co.edu.uco.jjskafee.movimientos.infraestructura.output.repositorios.estructurados.PgMovimientoRepository;
import co.edu.uco.jjskafee.movimientos.servicios.casouso.ConsultarMovimientos;
import co.edu.uco.jjskafee.movimientos.servicios.input.ports.ConsultarMovimientosUseCase;
import co.edu.uco.jjskafee.movimientos.servicios.output.ports.MovimientoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.ZoneId;

/** Arma el módulo de movimientos: crea su adaptador de salida y el caso de uso (Java puro) y los conecta. */
@Configuration
public class MovimientosConfig {

    @Bean
    public MovimientoRepository movimientoRepository(NamedParameterJdbcTemplate jdbc,
                                                     @Value("${jjskaffee.zona-horaria}") ZoneId zonaHoraria) {
        return new PgMovimientoRepository(jdbc, zonaHoraria);
    }

    @Bean
    public ConsultarMovimientosUseCase consultarMovimientos(MovimientoRepository movimientos,
                                                            UnidadDeTrabajo unidadDeTrabajo) {
        return new ConsultarMovimientos(movimientos, unidadDeTrabajo);
    }
}
