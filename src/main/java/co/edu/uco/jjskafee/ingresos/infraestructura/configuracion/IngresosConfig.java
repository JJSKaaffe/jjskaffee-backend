package co.edu.uco.jjskafee.ingresos.infraestructura.configuracion;

import co.edu.uco.jjskafee.compartido.servicios.output.ports.CatalogoRepository;
import co.edu.uco.jjskafee.compartido.servicios.output.ports.UnidadDeTrabajo;
import co.edu.uco.jjskafee.ingresos.infraestructura.output.repositorios.estructurados.PgCafeteriaRepository;
import co.edu.uco.jjskafee.ingresos.infraestructura.output.repositorios.estructurados.PgVentaRepository;
import co.edu.uco.jjskafee.ingresos.servicios.casouso.RegistrarIngreso;
import co.edu.uco.jjskafee.ingresos.servicios.input.ports.RegistrarIngresoUseCase;
import co.edu.uco.jjskafee.ingresos.servicios.output.ports.CafeteriaRepository;
import co.edu.uco.jjskafee.ingresos.servicios.output.ports.VentaRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;

/** Arma el módulo de ingresos: crea sus adaptadores de salida y el caso de uso (Java puro) y los conecta. */
@Configuration
public class IngresosConfig {

    @Bean
    public VentaRepository ventaRepository(JdbcTemplate jdbc) {
        return new PgVentaRepository(jdbc);
    }

    @Bean
    public CafeteriaRepository cafeteriaRepository(JdbcTemplate jdbc) {
        return new PgCafeteriaRepository(jdbc);
    }

    @Bean
    public RegistrarIngresoUseCase registrarIngreso(CatalogoRepository catalogo, VentaRepository ventas,
                                                    CafeteriaRepository cafeterias,
                                                    UnidadDeTrabajo unidadDeTrabajo, Clock reloj) {
        return new RegistrarIngreso(catalogo, ventas, cafeterias, unidadDeTrabajo, reloj);
    }
}
