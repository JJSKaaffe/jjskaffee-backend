package co.edu.uco.jjskafee.compartido.servicios.output.ports;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Puerto de salida: ejecuta una operación dentro de UNA transacción y con el
 * aislamiento de la cafetería activado (RLS). Si algo falla, no se guarda nada (ADR-0002).
 */
public interface UnidadDeTrabajo {

    <T> T ejecutar(UUID idCafeteria, Supplier<T> operacion);
}
