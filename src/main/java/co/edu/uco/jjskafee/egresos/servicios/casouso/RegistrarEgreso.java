package co.edu.uco.jjskafee.egresos.servicios.casouso;

import co.edu.uco.jjskafee.compartido.dominio.Categoria;
import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.RecursoNoEncontradoException;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.SinPermisoException;
import co.edu.uco.jjskafee.compartido.servicios.output.ports.CatalogoRepository;
import co.edu.uco.jjskafee.compartido.servicios.output.ports.UnidadDeTrabajo;
import co.edu.uco.jjskafee.egresos.dominio.Egreso;
import co.edu.uco.jjskafee.egresos.servicios.input.dto.request.EgresoRequest;
import co.edu.uco.jjskafee.egresos.servicios.input.dto.response.EgresoResponse;
import co.edu.uco.jjskafee.egresos.servicios.input.ports.RegistrarEgresoUseCase;
import co.edu.uco.jjskafee.egresos.servicios.output.ports.EgresoRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Caso de uso: registrar un egreso (RF14).
 * Es Java puro: no tiene anotaciones de Spring. Lo crea EgresosConfig.
 */
public class RegistrarEgreso implements RegistrarEgresoUseCase {

    private final CatalogoRepository catalogo;
    private final EgresoRepository egresos;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final Clock reloj;

    public RegistrarEgreso(CatalogoRepository catalogo, EgresoRepository egresos,
                           UnidadDeTrabajo unidadDeTrabajo, Clock reloj) {
        this.catalogo = Objects.requireNonNull(catalogo);
        this.egresos = Objects.requireNonNull(egresos);
        this.unidadDeTrabajo = Objects.requireNonNull(unidadDeTrabajo);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public EgresoResponse registrar(EgresoRequest solicitud, UsuarioAutenticado usuario) {
        // Se revisa el rol antes de tocar la base de datos
        if (!usuario.esAdministrador()) {
            throw new SinPermisoException("Solo el administrador puede registrar egresos");
        }
        return unidadDeTrabajo.ejecutar(usuario.idCafeteria(), () -> {
            Categoria categoria = catalogo.buscarCategoria(solicitud.idCategoria())
                    .orElseThrow(() -> new RecursoNoEncontradoException("La categoría no existe"));

            Egreso egreso = Egreso.registrar(usuario, categoria, solicitud.monto(),
                    solicitud.descripcion(), solicitud.comprobante(), Instant.now(reloj));

            egresos.guardar(egreso);
            return EgresoResponse.desde(egreso);
        });
    }
}
