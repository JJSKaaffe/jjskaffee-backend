package co.edu.uco.jjskafee.compartido.servicios.output.ports;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Puerto de salida: ejecuta una operación dentro de UNA transacción con el aislamiento de RLS
 * activado. Si algo falla, no se guarda nada (ADR-0002).
 */
public interface UnidadDeTrabajo {

    /** Con la cafetería fijada: solo se ven y se escriben datos de esa cafetería. Toda operación de negocio usa este. */
    <T> T ejecutar(UUID idCafeteria, Supplier<T> operacion);

    /**
     * Con la identidad (uid de Firebase) fijada y SIN cafetería: solo se ven las membresías de esa persona
     * y las cafeterías donde tiene una activa. Es solo para "mis cafeterías" (ADR-0016).
     */
    <T> T ejecutarConIdentidad(String firebaseUid, Supplier<T> operacion);
}
