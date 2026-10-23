package co.edu.uco.jjskafee.movimientos.servicios.casouso;

import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.compartido.dominio.enums.Permiso;
import co.edu.uco.jjskafee.compartido.servicios.output.ports.UnidadDeTrabajo;
import co.edu.uco.jjskafee.movimientos.dominio.FiltroMovimientos;
import co.edu.uco.jjskafee.movimientos.dominio.ResultadoMovimientos;
import co.edu.uco.jjskafee.movimientos.servicios.input.dto.request.FiltroMovimientosRequest;
import co.edu.uco.jjskafee.movimientos.servicios.input.dto.response.MovimientosResponse;
import co.edu.uco.jjskafee.movimientos.servicios.input.ports.ConsultarMovimientosUseCase;
import co.edu.uco.jjskafee.movimientos.servicios.output.ports.MovimientoRepository;

import java.util.Objects;
import java.util.UUID;

/** Caso de uso: consultar ingresos y egresos paginados, con totales calculados en la BD (RF17, RF38). */
public class ConsultarMovimientos implements ConsultarMovimientosUseCase {

    private final MovimientoRepository movimientos;
    private final UnidadDeTrabajo unidadDeTrabajo;

    public ConsultarMovimientos(MovimientoRepository movimientos, UnidadDeTrabajo unidadDeTrabajo) {
        this.movimientos = Objects.requireNonNull(movimientos);
        this.unidadDeTrabajo = Objects.requireNonNull(unidadDeTrabajo);
    }

    @Override
    public MovimientosResponse consultar(FiltroMovimientosRequest solicitud, UsuarioAutenticado usuario) {
        // RF9: el administrador ve todo; el cajero solo lo que él registró (ventas, ingresos libres y egresos)
        UUID soloDelUsuario = usuario.tienePermiso(Permiso.VER_TODOS_LOS_MOVIMIENTOS) ? null : usuario.id();

        FiltroMovimientos filtro = new FiltroMovimientos(solicitud.desde(), solicitud.hasta(), solicitud.tipo(),
                solicitud.idCategoria(), soloDelUsuario, solicitud.paginaOCero(), solicitud.tamanoOPorDefecto());

        ResultadoMovimientos resultado = unidadDeTrabajo.ejecutar(usuario.idCafeteria(),
                () -> movimientos.buscar(filtro));

        return MovimientosResponse.desde(resultado, filtro);
    }
}
