package co.edu.uco.jjskafee.acceso.servicios.output.ports;

import co.edu.uco.jjskafee.acceso.dominio.CafeteriaDelUsuario;
import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;

import java.util.List;
import java.util.Optional;

/** Puerto de salida: membresías (tabla usuarios, una fila por persona y cafetería, ADR-0016). */
public interface MembresiaRepository {

    /**
     * Membresía activa de la persona en la cafetería fijada en la transacción (UnidadDeTrabajo.ejecutar).
     * Si es de otra cafetería, RLS no la deja ver.
     */
    Optional<UsuarioAutenticado> buscarActivoPorFirebaseUid(String firebaseUid);

    /** Cafeterías donde la persona tiene una membresía activa (UnidadDeTrabajo.ejecutarConIdentidad). */
    List<CafeteriaDelUsuario> buscarCafeteriasDe(String firebaseUid);
}
