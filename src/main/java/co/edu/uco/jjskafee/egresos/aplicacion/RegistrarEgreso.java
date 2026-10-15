package co.edu.uco.jjskafee.egresos.aplicacion;

import co.edu.uco.jjskafee.compartido.aplicacion.puertos.UnidadDeTrabajo;
import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.RecursoNoEncontradoException;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.SinPermisoException;
import co.edu.uco.jjskafee.egresos.aplicacion.comandos.RegistrarEgresoComando;
import co.edu.uco.jjskafee.egresos.aplicacion.puertos.entrada.RegistrarEgresoCasoUso;
import co.edu.uco.jjskafee.egresos.aplicacion.puertos.salida.CategoriasEgreso;
import co.edu.uco.jjskafee.egresos.aplicacion.puertos.salida.RepositorioEgresos;
import co.edu.uco.jjskafee.egresos.dominio.CategoriaEgreso;
import co.edu.uco.jjskafee.egresos.dominio.Egreso;
import co.edu.uco.jjskafee.egresos.dominio.MensajesEgresos;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Caso de uso: registrar un egreso (RF14). Java puro: lo crea EgresosConfiguracion. */
public class RegistrarEgreso implements RegistrarEgresoCasoUso {

    private final CategoriasEgreso categorias;
    private final RepositorioEgresos egresos;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final Clock reloj;

    public RegistrarEgreso(CategoriasEgreso categorias, RepositorioEgresos egresos,
                           UnidadDeTrabajo unidadDeTrabajo, Clock reloj) {
        this.categorias = Objects.requireNonNull(categorias);
        this.egresos = Objects.requireNonNull(egresos);
        this.unidadDeTrabajo = Objects.requireNonNull(unidadDeTrabajo);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public Egreso registrar(RegistrarEgresoComando comando, UsuarioAutenticado usuario) {
        // El rol se revisa antes de tocar la base de datos (EXP04-01)
        if (!usuario.esAdministrador()) {
            throw new SinPermisoException(MensajesEgresos.SOLO_ADMINISTRADOR);
        }
        return unidadDeTrabajo.ejecutar(usuario.idCafeteria(), () -> {
            CategoriaEgreso categoria = categorias.buscar(usuario.idCafeteria(), comando.idCategoria())
                    .orElseThrow(() -> new RecursoNoEncontradoException(MensajesEgresos.CATEGORIA_NO_EXISTE));
            Egreso egreso = Egreso.registrar(usuario, categoria, comando.monto(), comando.descripcion(),
                    comando.comprobante(), Instant.now(reloj));
            egresos.guardar(egreso);
            return egreso;
        });
    }
}
