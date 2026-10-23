package co.edu.uco.jjskafee.acceso.servicios.casouso;

import co.edu.uco.jjskafee.acceso.dominio.Identidad;
import co.edu.uco.jjskafee.acceso.servicios.input.dto.response.CafeteriaDelUsuarioResponse;
import co.edu.uco.jjskafee.acceso.servicios.input.dto.response.UsuarioActualResponse;
import co.edu.uco.jjskafee.acceso.servicios.input.ports.ConsultarAccesoUseCase;
import co.edu.uco.jjskafee.acceso.servicios.output.ports.MembresiaRepository;
import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.compartido.servicios.output.ports.UnidadDeTrabajo;

import java.util.List;
import java.util.Objects;

/** Caso de uso: cafeterías del usuario (para elegir una) y su rol y permisos en la elegida (ADR-0016). */
public class ConsultarAcceso implements ConsultarAccesoUseCase {

    private final MembresiaRepository membresias;
    private final UnidadDeTrabajo unidadDeTrabajo;

    public ConsultarAcceso(MembresiaRepository membresias, UnidadDeTrabajo unidadDeTrabajo) {
        this.membresias = Objects.requireNonNull(membresias);
        this.unidadDeTrabajo = Objects.requireNonNull(unidadDeTrabajo);
    }

    @Override
    public List<CafeteriaDelUsuarioResponse> misCafeterias(Identidad identidad) {
        // La única consulta sin cafetería fijada: RLS solo deja ver las membresías de esta persona
        return unidadDeTrabajo.ejecutarConIdentidad(identidad.uid(), () -> membresias.buscarCafeteriasDe(identidad.uid()))
                .stream()
                .map(CafeteriaDelUsuarioResponse::desde)
                .toList();
    }

    @Override
    public UsuarioActualResponse yo(UsuarioAutenticado usuario) {
        return UsuarioActualResponse.desde(usuario);
    }
}
