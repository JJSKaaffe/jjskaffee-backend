package co.edu.uco.jjskafee.ingresos.infraestructura.configuracion;

import co.edu.uco.jjskafee.catalogo.api.CatalogoApi;
import co.edu.uco.jjskafee.compartido.aplicacion.puertos.ProveedorMensajes;
import co.edu.uco.jjskafee.compartido.aplicacion.puertos.UnidadDeTrabajo;
import co.edu.uco.jjskafee.ingresos.aplicacion.RegistrarIngreso;
import co.edu.uco.jjskafee.ingresos.aplicacion.puertos.entrada.RegistrarIngresoCasoUso;
import co.edu.uco.jjskafee.ingresos.infraestructura.persistencia.PgRepositorioVentas;
import co.edu.uco.jjskafee.ingresos.infraestructura.salida.AdaptadorCatalogoProductos;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;

/** Beans del módulo ingresos. Del catálogo solo recibe CatalogoApi. */
@Configuration(proxyBeanMethods = false)
public class IngresosConfiguracion {

    @Bean
    RegistrarIngresoCasoUso registrarIngreso(CatalogoApi catalogo, JdbcTemplate jdbc, UnidadDeTrabajo unidadDeTrabajo,
                                             ProveedorMensajes mensajes, Clock reloj) {
        return new RegistrarIngreso(new AdaptadorCatalogoProductos(catalogo), new PgRepositorioVentas(jdbc),
                unidadDeTrabajo, mensajes, reloj);
    }
}
