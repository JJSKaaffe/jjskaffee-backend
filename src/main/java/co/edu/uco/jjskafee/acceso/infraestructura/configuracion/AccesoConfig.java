package co.edu.uco.jjskafee.acceso.infraestructura.configuracion;

import co.edu.uco.jjskafee.acceso.infraestructura.output.repositorios.estructurados.PgUsuarioRepository;
import co.edu.uco.jjskafee.acceso.servicios.casouso.AutenticarUsuario;
import co.edu.uco.jjskafee.acceso.servicios.casouso.ConsultarAcceso;
import co.edu.uco.jjskafee.acceso.servicios.input.ports.AutenticarUsuarioUseCase;
import co.edu.uco.jjskafee.acceso.servicios.input.ports.ConsultarAccesoUseCase;
import co.edu.uco.jjskafee.acceso.servicios.output.ports.MembresiaRepository;
import co.edu.uco.jjskafee.acceso.servicios.output.ports.VerificadorDeToken;
import co.edu.uco.jjskafee.compartido.servicios.output.ports.UnidadDeTrabajo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Arma el módulo de acceso: membresías y casos de uso. El VerificadorDeToken lo crea FirebaseConfig
 * (o, en las pruebas de integración, ConfiguracionDePrueba).
 */
@Configuration
public class AccesoConfig {

    @Bean
    public MembresiaRepository membresiaRepository(JdbcTemplate jdbc) {
        return new PgUsuarioRepository(jdbc);
    }

    @Bean
    public AutenticarUsuarioUseCase autenticarUsuario(VerificadorDeToken verificador, MembresiaRepository membresias,
                                                      UnidadDeTrabajo unidadDeTrabajo) {
        return new AutenticarUsuario(verificador, membresias, unidadDeTrabajo);
    }

    @Bean
    public ConsultarAccesoUseCase consultarAcceso(MembresiaRepository membresias, UnidadDeTrabajo unidadDeTrabajo) {
        return new ConsultarAcceso(membresias, unidadDeTrabajo);
    }
}
