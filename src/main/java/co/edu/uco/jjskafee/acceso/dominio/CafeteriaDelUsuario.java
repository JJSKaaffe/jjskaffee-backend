package co.edu.uco.jjskafee.acceso.dominio;

import co.edu.uco.jjskafee.compartido.dominio.enums.Rol;

import java.util.Objects;
import java.util.UUID;

/**
 * Una cafetería donde la persona tiene una membresía activa, con su rol en ella.
 * "activa" es el estado de la cafetería: en una inactiva no se pueden registrar ingresos.
 */
public record CafeteriaDelUsuario(UUID idCafeteria, String nombre, Rol rol, boolean activa) {

    public CafeteriaDelUsuario {
        Objects.requireNonNull(idCafeteria, "La cafetería es obligatoria");
        Objects.requireNonNull(rol, "El rol en la cafetería es obligatorio");
    }
}
