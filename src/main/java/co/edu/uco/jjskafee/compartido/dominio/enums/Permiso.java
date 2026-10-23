package co.edu.uco.jjskafee.compartido.dominio.enums;

/**
 * Lo que un usuario puede hacer dentro de una cafetería. Se deriva de su rol en esa cafetería
 * (Rol.permisos()); el frontend lo usa para mostrar el menú que corresponde.
 */
public enum Permiso {
    REGISTRAR_VENTA,
    REGISTRAR_INGRESO_LIBRE,
    REGISTRAR_EGRESO,
    VER_TODOS_LOS_MOVIMIENTOS
}
