package co.edu.uco.jjskafee.catalogo.infraestructura.configuracion;

import co.edu.uco.jjskafee.catalogo.aplicacion.ServicioCatalogo;
import co.edu.uco.jjskafee.catalogo.infraestructura.persistencia.PgRepositorioCatalogo;
import co.edu.uco.jjskafee.compartido.aplicacion.puertos.UnidadDeTrabajo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Beans del módulo catálogo. Un solo ServicioCatalogo cumple dos papeles:
 * CatalogoApi (para otros módulos) y ConsultarCatalogo (para su controlador).
 */
@Configuration(proxyBeanMethods = false)
public class CatalogoConfiguracion {

    @Bean
    ServicioCatalogo servicioCatalogo(JdbcTemplate jdbc, UnidadDeTrabajo unidadDeTrabajo) {
        return new ServicioCatalogo(new PgRepositorioCatalogo(jdbc), unidadDeTrabajo);
    }
}
