package co.edu.uco.jjskafee.compartido.dominio.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Rol del usuario dentro de una cafetería. Una persona puede tener un rol distinto en cada
 * cafetería (ADR-0016). Este es el único lugar donde se decide qué permisos da cada rol.
 */
public enum Rol {
    /** Ve todo lo de la cafetería y registra ventas, ingresos libres y egresos. */
    ADMINISTRADOR(EnumSet.allOf(Permiso.class)),
    /** Solo registra ventas y ve lo que él mismo registró. */
    CAJERO(EnumSet.of(Permiso.REGISTRAR_VENTA));

    private final Set<Permiso> permisos;

    Rol(EnumSet<Permiso> permisos) {
        this.permisos = Collections.unmodifiableSet(permisos);
    }

    /** Permisos del rol, en el orden en que están declarados en Permiso. */
    public Set<Permiso> permisos() {
        return permisos;
    }

    public boolean tiene(Permiso permiso) {
        return permisos.contains(permiso);
    }
}
