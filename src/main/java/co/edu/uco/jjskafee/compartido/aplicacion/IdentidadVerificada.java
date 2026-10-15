package co.edu.uco.jjskafee.compartido.aplicacion;

import java.util.Objects;
import java.util.UUID;

/** Lo que garantiza un token válido: el uid de Firebase y la cafetería (custom claim · SPIKE-01). */
public record IdentidadVerificada(String firebaseUid, UUID idCafeteria) {

    public IdentidadVerificada {
        Objects.requireNonNull(firebaseUid);
        Objects.requireNonNull(idCafeteria);
    }
}
