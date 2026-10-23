package co.edu.uco.jjskafee.acceso.servicios.casouso;

import co.edu.uco.jjskafee.acceso.dominio.Identidad;
import co.edu.uco.jjskafee.acceso.servicios.input.ports.AutenticarUsuarioUseCase;
import co.edu.uco.jjskafee.acceso.servicios.output.ports.MembresiaRepository;
import co.edu.uco.jjskafee.acceso.servicios.output.ports.VerificadorDeToken;
import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.SinPermisoException;
import co.edu.uco.jjskafee.compartido.servicios.output.ports.UnidadDeTrabajo;

import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso: identificar a la persona por su token y darle el rol de la cafetería que eligió
 * (ADR-0016). La membresía se lee con RLS de esa cafetería, en cada petición: un cambio de rol
 * o una desactivación aplica de inmediato.
 */
public class AutenticarUsuario implements AutenticarUsuarioUseCase {

    private final VerificadorDeToken verificador;
    private final MembresiaRepository membresias;
    private final UnidadDeTrabajo unidadDeTrabajo;

    public AutenticarUsuario(VerificadorDeToken verificador, MembresiaRepository membresias,
                             UnidadDeTrabajo unidadDeTrabajo) {
        this.verificador = Objects.requireNonNull(verificador);
        this.membresias = Objects.requireNonNull(membresias);
        this.unidadDeTrabajo = Objects.requireNonNull(unidadDeTrabajo);
    }

    @Override
    public Identidad identificar(String token) {
        return verificador.verificar(token);
    }

    @Override
    public UsuarioAutenticado autorizarEnCafeteria(Identidad identidad, UUID idCafeteria) {
        Objects.requireNonNull(identidad, "La identidad es obligatoria");
        Objects.requireNonNull(idCafeteria, "La cafetería es obligatoria");
        // Mismo mensaje si la cafetería no existe o si no pertenece a ella: no se revela qué cafeterías existen
        return unidadDeTrabajo.ejecutar(idCafeteria, () -> membresias.buscarActivoPorFirebaseUid(identidad.uid()))
                .filter(usuario -> usuario.idCafeteria().equals(idCafeteria))
                .orElseThrow(() -> new SinPermisoException("No tienes acceso a esta cafetería"));
    }
}
