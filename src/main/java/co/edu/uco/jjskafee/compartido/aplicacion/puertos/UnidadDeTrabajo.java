package co.edu.uco.jjskafee.compartido.aplicacion.puertos;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Ejecuta una operación dentro de UNA transacción y con el aislamiento de la cafetería
 * activado (RLS con app.current_cafeteria). Si algo falla no se guarda nada (ADR-0002).
 * Si ya hay una transacción abierta para la misma cafetería, la operación se une a ella.
 */
public interface UnidadDeTrabajo {

    <T> T ejecutar(UUID idCafeteria, Supplier<T> operacion);
}
