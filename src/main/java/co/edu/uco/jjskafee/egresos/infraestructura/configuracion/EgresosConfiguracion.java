package co.edu.uco.jjskafee.egresos.infraestructura.configuracion;

import co.edu.uco.jjskafee.catalogo.api.CatalogoApi;
import co.edu.uco.jjskafee.compartido.aplicacion.puertos.UnidadDeTrabajo;
import co.edu.uco.jjskafee.egresos.aplicacion.RegistrarEgreso;
import co.edu.uco.jjskafee.egresos.aplicacion.puertos.entrada.RegistrarEgresoCasoUso;
import co.edu.uco.jjskafee.egresos.infraestructura.persistencia.PgRepositorioEgresos;
import co.edu.uco.jjskafee.egresos.infraestructura.salida.AdaptadorCategoriasEgreso;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;

/** Beans del módulo egresos. Del catálogo solo recibe CatalogoApi. */
@Configuration(proxyBeanMethods = false)
public class EgresosConfiguracion {

    @Bean
    RegistrarEgresoCasoUso registrarEgreso(CatalogoApi catalogo, JdbcTemplate jdbc, UnidadDeTrabajo unidadDeTrabajo,
                                           Clock reloj) {
        return new RegistrarEgreso(new AdaptadorCategoriasEgreso(catalogo), new PgRepositorioEgresos(jdbc),
                unidadDeTrabajo, reloj);
    }
}
