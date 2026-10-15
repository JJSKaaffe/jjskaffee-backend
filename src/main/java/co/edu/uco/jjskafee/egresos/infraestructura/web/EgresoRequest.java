package co.edu.uco.jjskafee.egresos.infraestructura.web;

import co.edu.uco.jjskafee.egresos.aplicacion.comandos.RegistrarEgresoComando;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cuerpo de POST /api/egresos. No trae idCafeteria, registradoPor ni fechaHora (ADR-0001, ADR-0007).
 * Los mensajes son claves de i18n/mensajes.properties; {max} lo llena el validador.
 */
public record EgresoRequest(
        @NotNull(message = "{validacion.egreso.categoria.obligatoria}") UUID idCategoria,
        @NotNull(message = "{validacion.egreso.monto.obligatorio}")
        @Positive(message = "{validacion.egreso.monto.positivo}") BigDecimal monto,
        @NotBlank(message = "{validacion.egreso.descripcion.obligatoria}")
        @Size(max = 200, message = "{validacion.texto.maximo}") String descripcion,
        @Size(max = 50, message = "{validacion.texto.maximo}") String comprobante
) {

    public RegistrarEgresoComando aComando() {
        return new RegistrarEgresoComando(idCategoria, monto, descripcion, comprobante);
    }
}
