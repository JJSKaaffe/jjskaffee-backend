package co.edu.uco.jjskafee.servicios.casouso;

import co.edu.uco.jjskafee.dominio.Movimiento;
import co.edu.uco.jjskafee.dominio.ResultadoMovimientos;
import co.edu.uco.jjskafee.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.dominio.excepciones.ReglaNegocioException;
import co.edu.uco.jjskafee.servicios.input.dto.request.FiltroMovimientosRequest;
import co.edu.uco.jjskafee.servicios.input.dto.response.MovimientosResponse;
import co.edu.uco.jjskafee.servicios.input.ports.ConsultarMovimientosUseCase;
import co.edu.uco.jjskafee.servicios.output.ports.MovimientoRepository;
import co.edu.uco.jjskafee.servicios.output.ports.UnidadDeTrabajo;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Caso de uso: consultar ingresos y egresos con totales. */
public class ConsultarMovimientos implements ConsultarMovimientosUseCase {

    private final MovimientoRepository movimientos;
    private final UnidadDeTrabajo unidadDeTrabajo;

    public ConsultarMovimientos(MovimientoRepository movimientos, UnidadDeTrabajo unidadDeTrabajo) {
        this.movimientos = Objects.requireNonNull(movimientos);
        this.unidadDeTrabajo = Objects.requireNonNull(unidadDeTrabajo);
    }

    @Override
    public MovimientosResponse consultar(FiltroMovimientosRequest filtro, UsuarioAutenticado usuario) {
        if (filtro.desde() != null && filtro.hasta() != null && filtro.desde().isAfter(filtro.hasta())) {
            throw new ReglaNegocioException("La fecha 'desde' no puede ser posterior a 'hasta'");
        }
        // RF9: el administrador ve todo; el cajero solo lo que él registró
        UUID soloDelUsuario = usuario.esAdministrador() ? null : usuario.id();

        List<Movimiento> lista = unidadDeTrabajo.ejecutar(usuario.idCafeteria(),
                () -> movimientos.buscar(filtro.desde(), filtro.hasta(), filtro.tipo(), soloDelUsuario));

        return MovimientosResponse.desde(ResultadoMovimientos.de(lista));
    }
}
