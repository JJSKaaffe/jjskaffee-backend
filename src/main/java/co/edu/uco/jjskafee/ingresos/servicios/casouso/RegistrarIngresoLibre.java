package co.edu.uco.jjskafee.ingresos.servicios.casouso;

import co.edu.uco.jjskafee.compartido.dominio.Categoria;
import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.compartido.dominio.enums.Permiso;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.RecursoNoEncontradoException;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.ReglaNegocioException;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.SinPermisoException;
import co.edu.uco.jjskafee.compartido.servicios.output.ports.CatalogoRepository;
import co.edu.uco.jjskafee.compartido.servicios.output.ports.UnidadDeTrabajo;
import co.edu.uco.jjskafee.ingresos.dominio.IngresoLibre;
import co.edu.uco.jjskafee.ingresos.servicios.input.dto.request.IngresoLibreRequest;
import co.edu.uco.jjskafee.ingresos.servicios.input.dto.response.IngresoLibreResponse;
import co.edu.uco.jjskafee.ingresos.servicios.input.ports.RegistrarIngresoLibreUseCase;
import co.edu.uco.jjskafee.ingresos.servicios.output.ports.CafeteriaRepository;
import co.edu.uco.jjskafee.ingresos.servicios.output.ports.IngresoLibreRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Caso de uso: registrar un ingreso libre (monto + categoría de ingreso). Solo el administrador.
 * Mismas garantías que una venta: cafetería activa, Idempotency-Key y todo en una sola transacción con RLS.
 */
public class RegistrarIngresoLibre implements RegistrarIngresoLibreUseCase {

    /** Separa los campos de la huella; no aparece en un texto normal. */
    private static final String SEPARADOR = "\u001F";

    private final CatalogoRepository catalogo;
    private final IngresoLibreRepository ingresos;
    private final CafeteriaRepository cafeterias;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final Clock reloj;

    public RegistrarIngresoLibre(CatalogoRepository catalogo, IngresoLibreRepository ingresos,
                                 CafeteriaRepository cafeterias, UnidadDeTrabajo unidadDeTrabajo, Clock reloj) {
        this.catalogo = Objects.requireNonNull(catalogo);
        this.ingresos = Objects.requireNonNull(ingresos);
        this.cafeterias = Objects.requireNonNull(cafeterias);
        this.unidadDeTrabajo = Objects.requireNonNull(unidadDeTrabajo);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public IngresoLibreResponse registrar(IngresoLibreRequest solicitud, UUID claveIdempotencia,
                                          UsuarioAutenticado usuario) {
        // Se revisa el rol antes de tocar la base de datos
        if (!usuario.tienePermiso(Permiso.REGISTRAR_INGRESO_LIBRE)) {
            throw new SinPermisoException("Solo el administrador puede registrar ingresos libres");
        }
        return unidadDeTrabajo.ejecutar(usuario.idCafeteria(), () -> {
            if (!cafeterias.estaActiva(usuario.idCafeteria())) {
                throw new ReglaNegocioException("La cafetería no está activa; no se pueden registrar ingresos");
            }

            String hashSolicitud = claveIdempotencia == null ? null : huellaDe(solicitud);
            if (claveIdempotencia != null) {
                Optional<IngresoLibre> previo = ingresos.buscarPorClaveIdempotencia(claveIdempotencia);
                if (previo.isPresent()) {
                    return repetido(previo.get(), hashSolicitud);
                }
            }

            Categoria categoria = catalogo.buscarCategoria(solicitud.idCategoria())
                    .orElseThrow(() -> new RecursoNoEncontradoException("La categoría no existe"));
            IngresoLibre ingreso = IngresoLibre.registrar(usuario, categoria, solicitud.monto(), solicitud.descripcion(),
                    solicitud.comprobante(), claveIdempotencia, hashSolicitud, Instant.now(reloj));

            if (!ingresos.guardar(ingreso)) {
                // Otra petición con la misma clave se guardó primero: se responde con ese ingreso
                IngresoLibre ganador = ingresos.buscarPorClaveIdempotencia(claveIdempotencia)
                        .orElseThrow(() -> new IllegalStateException("El ingreso con la clave repetida no se encontró"));
                return repetido(ganador, hashSolicitud);
            }
            return IngresoLibreResponse.desde(ingreso);
        });
    }

    /** Reintento con la misma Idempotency-Key: solo vale si pide exactamente lo mismo. */
    private static IngresoLibreResponse repetido(IngresoLibre previo, String hashSolicitud) {
        previo.verificarMismaSolicitud(hashSolicitud);
        return IngresoLibreResponse.desde(previo);
    }

    /**
     * Huella de lo que se pidió: categoría, monto, descripción y comprobante, como se guardan
     * (1000 y 1000.00 son el mismo monto; los espacios de los extremos no cuentan).
     */
    static String huellaDe(IngresoLibreRequest solicitud) {
        String monto = solicitud.monto() == null ? "" : solicitud.monto().stripTrailingZeros().toPlainString();
        String descripcion = solicitud.descripcion() == null ? "" : solicitud.descripcion().strip();
        String comprobante = solicitud.comprobante() == null ? "" : solicitud.comprobante().strip();
        return HuellaDeSolicitud.sha256(String.join(SEPARADOR, String.valueOf(solicitud.idCategoria()), monto,
                descripcion, comprobante));
    }
}
