package co.edu.uco.jjskafee.ingresos.infraestructura.configuracion;

import co.edu.uco.jjskafee.compartido.servicios.output.ports.CatalogoRepository;
import co.edu.uco.jjskafee.compartido.servicios.output.ports.UnidadDeTrabajo;
import co.edu.uco.jjskafee.ingresos.infraestructura.output.repositorios.estructurados.PgCafeteriaRepository;
import co.edu.uco.jjskafee.ingresos.infraestructura.output.repositorios.estructurados.PgCatalogoDeVentaRepository;
import co.edu.uco.jjskafee.ingresos.infraestructura.output.repositorios.estructurados.PgIngresoLibreRepository;
import co.edu.uco.jjskafee.ingresos.infraestructura.output.repositorios.estructurados.PgVentaRepository;
import co.edu.uco.jjskafee.ingresos.servicios.casouso.ConsultarCatalogoDeVenta;
import co.edu.uco.jjskafee.ingresos.servicios.casouso.RegistrarIngreso;
import co.edu.uco.jjskafee.ingresos.servicios.casouso.RegistrarIngresoLibre;
import co.edu.uco.jjskafee.ingresos.servicios.input.ports.ConsultarCatalogoDeVentaUseCase;
import co.edu.uco.jjskafee.ingresos.servicios.input.ports.RegistrarIngresoLibreUseCase;
import co.edu.uco.jjskafee.ingresos.servicios.input.ports.RegistrarIngresoUseCase;
import co.edu.uco.jjskafee.ingresos.servicios.output.ports.CafeteriaRepository;
import co.edu.uco.jjskafee.ingresos.servicios.output.ports.CatalogoDeVentaRepository;
import co.edu.uco.jjskafee.ingresos.servicios.output.ports.IngresoLibreRepository;
import co.edu.uco.jjskafee.ingresos.servicios.output.ports.VentaRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;

/** Arma el módulo de ingresos: crea sus adaptadores de salida y los casos de uso (Java puro) y los conecta. */
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
    public IngresoLibreRepository ingresoLibreRepository(JdbcTemplate jdbc) {
        return new PgIngresoLibreRepository(jdbc);
    }

    @Bean
    public CatalogoDeVentaRepository catalogoDeVentaRepository(JdbcTemplate jdbc) {
        return new PgCatalogoDeVentaRepository(jdbc);
    }

    @Bean
    public RegistrarIngresoUseCase registrarIngreso(CatalogoRepository catalogo, VentaRepository ventas,
                                                    CafeteriaRepository cafeterias,
                                                    UnidadDeTrabajo unidadDeTrabajo, Clock reloj) {
        return new RegistrarIngreso(catalogo, ventas, cafeterias, unidadDeTrabajo, reloj);
    }

    @Bean
    public RegistrarIngresoLibreUseCase registrarIngresoLibre(CatalogoRepository catalogo,
                                                              IngresoLibreRepository ingresos,
                                                              CafeteriaRepository cafeterias,
                                                              UnidadDeTrabajo unidadDeTrabajo, Clock reloj) {
        return new RegistrarIngresoLibre(catalogo, ingresos, cafeterias, unidadDeTrabajo, reloj);
    }

    @Bean
    public ConsultarCatalogoDeVentaUseCase consultarCatalogoDeVenta(CatalogoDeVentaRepository catalogo,
                                                                    UnidadDeTrabajo unidadDeTrabajo) {
        return new ConsultarCatalogoDeVenta(catalogo, unidadDeTrabajo);
    }
}
