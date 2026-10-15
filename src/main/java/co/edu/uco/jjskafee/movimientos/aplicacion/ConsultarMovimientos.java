package co.edu.uco.jjskafee.movimientos.aplicacion;

import co.edu.uco.jjskafee.compartido.aplicacion.puertos.ProveedorMensajes;
import co.edu.uco.jjskafee.compartido.aplicacion.puertos.UnidadDeTrabajo;
import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.movimientos.aplicacion.puertos.entrada.ConsultarMovimientosCasoUso;
import co.edu.uco.jjskafee.movimientos.aplicacion.puertos.salida.RepositorioMovimientos;
import co.edu.uco.jjskafee.movimientos.dominio.FiltroMovimientos;
import co.edu.uco.jjskafee.movimientos.dominio.MensajesMovimientos;
import co.edu.uco.jjskafee.movimientos.dominio.Movimiento;
import co.edu.uco.jjskafee.movimientos.dominio.PaginaMovimientos;
import co.edu.uco.jjskafee.movimientos.dominio.ResumenMovimientos;
import co.edu.uco.jjskafee.movimientos.dominio.TipoMovimiento;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso: consultar ingresos y egresos con totales.
 * RF9: el administrador ve todo; el cajero solo lo que él registró.
 * Java puro: lo crea MovimientosConfiguracion.
 */
public class ConsultarMovimientos implements ConsultarMovimientosCasoUso {

    private final RepositorioMovimientos movimientos;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final ProveedorMensajes mensajes;

    public ConsultarMovimientos(RepositorioMovimientos movimientos, UnidadDeTrabajo unidadDeTrabajo,
                                ProveedorMensajes mensajes) {
        this.movimientos = Objects.requireNonNull(movimientos);
        this.unidadDeTrabajo = Objects.requireNonNull(unidadDeTrabajo);
        this.mensajes = Objects.requireNonNull(mensajes);
    }

    @Override
    public PaginaMovimientos consultar(FiltroMovimientos filtro, UsuarioAutenticado usuario) {
        UUID idCafeteria = usuario.idCafeteria();
        UUID soloDelUsuario = usuario.esAdministrador() ? null : usuario.id();

        return unidadDeTrabajo.ejecutar(idCafeteria, () -> {
            ResumenMovimientos resumen =movimientos.resumir(idCafeteria, filtro, soloDelUsuario);
            List<Movimiento> lista = movimientos.buscar(idCafeteria, filtro, soloDelUsuario).stream()
                    .map(this::conDescripcionTraducida)
                    .toList();
            return new PaginaMovimientos(lista, resumen.totales(), filtro.pagina(), filtro.tamano(),
                    resumen.cantidad());
        });
    }

    /** La vista trae "Venta" fijo para los ingresos; el texto que ve el usuario sale de mensajes.properties. */
    private Movimiento conDescripcionTraducida(Movimiento m) {
        return m.tipo() == TipoMovimiento.INGRESO
                ? m.conDescripcion(mensajes.obtener(MensajesMovimientos.DESCRIPCION_VENTA))
                : m;
    }
}
