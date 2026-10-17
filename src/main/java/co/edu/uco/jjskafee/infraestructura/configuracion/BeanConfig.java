package co.edu.uco.jjskafee.infraestructura.configuracion;

import co.edu.uco.jjskafee.infraestructura.output.repositorios.estructurados.PgCafeteriaRepository;
import co.edu.uco.jjskafee.infraestructura.output.repositorios.estructurados.PgCatalogoRepository;
import co.edu.uco.jjskafee.infraestructura.output.repositorios.estructurados.PgEgresoRepository;
import co.edu.uco.jjskafee.infraestructura.output.repositorios.estructurados.PgMovimientoRepository;
import co.edu.uco.jjskafee.infraestructura.output.repositorios.estructurados.PgUnidadDeTrabajo;
import co.edu.uco.jjskafee.infraestructura.output.repositorios.estructurados.PgVentaRepository;
import co.edu.uco.jjskafee.servicios.casouso.ConsultarMovimientos;
import co.edu.uco.jjskafee.servicios.casouso.RegistrarEgreso;
import co.edu.uco.jjskafee.servicios.casouso.RegistrarIngreso;
import co.edu.uco.jjskafee.servicios.input.ports.ConsultarMovimientosUseCase;
import co.edu.uco.jjskafee.servicios.input.ports.RegistrarEgresoUseCase;
import co.edu.uco.jjskafee.servicios.input.ports.RegistrarIngresoUseCase;
import co.edu.uco.jjskafee.servicios.output.ports.CafeteriaRepository;
import co.edu.uco.jjskafee.servicios.output.ports.CatalogoRepository;
import co.edu.uco.jjskafee.servicios.output.ports.EgresoRepository;
import co.edu.uco.jjskafee.servicios.output.ports.MovimientoRepository;
import co.edu.uco.jjskafee.servicios.output.ports.UnidadDeTrabajo;
import co.edu.uco.jjskafee.servicios.output.ports.VentaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Arma la hexagonal: crea los adaptadores de salida y los casos de uso (que son Java puro,
 * sin anotaciones de Spring) y los conecta por sus puertos.
 */
@Configuration
public class BeanConfig {

    /** Reloj del servidor: la fecha y hora de cada movimiento la pone el backend (ADR-0007, RF43). */
    @Bean
    public Clock reloj() {
        return Clock.systemUTC();
    }

    // ----- Adaptadores de salida (PostgreSQL) -----

    @Bean
    public UnidadDeTrabajo unidadDeTrabajo(PlatformTransactionManager transacciones, JdbcTemplate jdbc) {
        return new PgUnidadDeTrabajo(new TransactionTemplate(transacciones), jdbc);
    }

    @Bean
    public CatalogoRepository catalogoRepository(JdbcTemplate jdbc) {
        return new PgCatalogoRepository(jdbc);
    }

    @Bean
    public VentaRepository ventaRepository(JdbcTemplate jdbc) {
        return new PgVentaRepository(jdbc);
    }

    @Bean
    public CafeteriaRepository cafeteriaRepository(JdbcTemplate jdbc) {
        return new PgCafeteriaRepository(jdbc);
    }

    @Bean
    public EgresoRepository egresoRepository(JdbcTemplate jdbc) {
        return new PgEgresoRepository(jdbc);
    }

    @Bean
    public MovimientoRepository movimientoRepository(NamedParameterJdbcTemplate jdbc,
                                                     @Value("${jjskaffee.zona-horaria}") ZoneId zonaHoraria) {
        return new PgMovimientoRepository(jdbc, zonaHoraria);
    }

    // ----- Casos de uso (puertos de entrada) -----

    @Bean
    public RegistrarIngresoUseCase registrarIngreso(CatalogoRepository catalogo, VentaRepository ventas,
                                                    CafeteriaRepository cafeterias,
                                                    UnidadDeTrabajo unidadDeTrabajo, Clock reloj) {
        return new RegistrarIngreso(catalogo, ventas, cafeterias, unidadDeTrabajo, reloj);
    }

    @Bean
    public RegistrarEgresoUseCase registrarEgreso(CatalogoRepository catalogo, EgresoRepository egresos,
                                                  UnidadDeTrabajo unidadDeTrabajo, Clock reloj) {
        return new RegistrarEgreso(catalogo, egresos, unidadDeTrabajo, reloj);
    }

    @Bean
    public ConsultarMovimientosUseCase consultarMovimientos(MovimientoRepository movimientos,
                                                            UnidadDeTrabajo unidadDeTrabajo) {
        return new ConsultarMovimientos(movimientos, unidadDeTrabajo);
    }
}
