package co.edu.uco.jjskafee.movimientos.infraestructura.configuracion;

import co.edu.uco.jjskafee.compartido.aplicacion.puertos.ProveedorMensajes;
import co.edu.uco.jjskafee.compartido.aplicacion.puertos.UnidadDeTrabajo;
import co.edu.uco.jjskafee.movimientos.aplicacion.ConsultarMovimientos;
import co.edu.uco.jjskafee.movimientos.aplicacion.puertos.entrada.ConsultarMovimientosCasoUso;
import co.edu.uco.jjskafee.movimientos.infraestructura.persistencia.PgRepositorioMovimientos;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** Beans del módulo movimientos. */
@Configuration(proxyBeanMethods = false)
public class MovimientosConfiguracion {

    @Bean
    ConsultarMovimientosCasoUso consultarMovimientos(JdbcTemplate jdbc, UnidadDeTrabajo unidadDeTrabajo,
                                                     ProveedorMensajes mensajes) {
        return new ConsultarMovimientos(new PgRepositorioMovimientos(jdbc), unidadDeTrabajo, mensajes);
    }
}
