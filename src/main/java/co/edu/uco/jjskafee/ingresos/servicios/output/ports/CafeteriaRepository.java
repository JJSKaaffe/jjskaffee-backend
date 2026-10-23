package co.edu.uco.jjskafee.ingresos.servicios.output.ports;

import java.util.UUID;

/** Puerto de salida: consultar el estado de la cafetería (tabla cafeteria, solo lectura). */
public interface CafeteriaRepository {

    /** true si la cafetería existe, es visible para la transacción actual (RLS) y está activa. */
    boolean estaActiva(UUID idCafeteria);
}
