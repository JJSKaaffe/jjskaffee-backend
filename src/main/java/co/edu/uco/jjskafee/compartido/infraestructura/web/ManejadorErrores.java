package co.edu.uco.jjskafee.compartido.infraestructura.web;

import co.edu.uco.jjskafee.compartido.aplicacion.puertos.ProveedorMensajes;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.ExcepcionAplicacion;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.NoAutenticadoException;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.RecursoNoEncontradoException;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.ReglaNegocioException;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.SinPermisoException;
import co.edu.uco.jjskafee.compartido.dominio.mensajes.CodigoMensaje;
import co.edu.uco.jjskafee.compartido.dominio.mensajes.MensajesCompartidos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Convierte cualquier error en una respuesta RFC 9457 (application/problem+json):
 *   { "status": 422, "title": "...", "detail": "...", "codigo": "egresos.monto.invalido",
 *     "errores": { "campo": "mensaje" }, "instante": "2026-10-14T23:00:00Z" }
 * Todos los textos salen de i18n/mensajes.properties. "codigo" le sirve al frontend
 * para reaccionar sin depender del texto.
 */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErrores.class);
    /** 422: el nombre de la constante cambió entre versiones de Spring, por eso se usa el número. */
    private static final HttpStatus REGLA_NEGOCIO_422 = HttpStatus.valueOf(422);

    private final ProveedorMensajes mensajes;
    private final MessageSource fuenteMensajes;
    private final Clock reloj;

    public ManejadorErrores(ProveedorMensajes mensajes, MessageSource fuenteMensajes, Clock reloj) {
        this.mensajes = Objects.requireNonNull(mensajes);
        this.fuenteMensajes = Objects.requireNonNull(fuenteMensajes);
        this.reloj = Objects.requireNonNull(reloj);
    }

    // ---------------- Errores esperados del negocio ----------------

    @ExceptionHandler(NoAutenticadoException.class)
    ResponseEntity<ProblemDetail> noAutenticado(NoAutenticadoException e) {
        return desdeExcepcion(HttpStatus.UNAUTHORIZED, MensajesCompartidos.TITULO_NO_AUTENTICADO, e);
    }

    @ExceptionHandler(SinPermisoException.class)
    ResponseEntity<ProblemDetail> sinPermiso(SinPermisoException e) {
        return desdeExcepcion(HttpStatus.FORBIDDEN, MensajesCompartidos.TITULO_SIN_PERMISO, e);
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    ResponseEntity<ProblemDetail> noEncontrado(RecursoNoEncontradoException e) {
        return desdeExcepcion(HttpStatus.NOT_FOUND, MensajesCompartidos.TITULO_NO_ENCONTRADO, e);
    }

    @ExceptionHandler(ReglaNegocioException.class)
    ResponseEntity<ProblemDetail> reglaNegocio(ReglaNegocioException e) {
        return desdeExcepcion(REGLA_NEGOCIO_422, MensajesCompartidos.TITULO_REGLA_NEGOCIO, e);
    }

    // ---------------- Errores de la petición ----------------

    /** IMP25-01: señala el campo exacto con un mensaje claro. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validacion(MethodArgumentNotValidException e) {
        Map<String, String> errores = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(campo -> errores.putIfAbsent(campo.getField(),
                fuenteMensajes.getMessage(campo, LocaleContextHolder.getLocale())));
        ResponseEntity<ProblemDetail> respuesta = construir(HttpStatus.BAD_REQUEST,
                MensajesCompartidos.TITULO_ERROR_SOLICITUD, MensajesCompartidos.SOLICITUD_INVALIDA);
        respuesta.getBody().setProperty("errores", errores);
        return respuesta;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> cuerpoIlegible(HttpMessageNotReadableException e) {
        return construir(HttpStatus.BAD_REQUEST, MensajesCompartidos.TITULO_ERROR_SOLICITUD,
                MensajesCompartidos.CUERPO_ILEGIBLE);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> parametroInvalido(MethodArgumentTypeMismatchException e) {
        return construir(HttpStatus.BAD_REQUEST, MensajesCompartidos.TITULO_ERROR_SOLICITUD,
                MensajesCompartidos.PARAMETRO_INVALIDO, e.getName());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ProblemDetail> parametroFaltante(MissingServletRequestParameterException e) {
        return construir(HttpStatus.BAD_REQUEST, MensajesCompartidos.TITULO_ERROR_SOLICITUD,
                MensajesCompartidos.PARAMETRO_INVALIDO, e.getParameterName());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> rutaNoExiste(NoResourceFoundException e) {
        return construir(HttpStatus.NOT_FOUND, MensajesCompartidos.TITULO_NO_ENCONTRADO,
                MensajesCompartidos.RUTA_NO_EXISTE);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> metodoNoPermitido(HttpRequestMethodNotSupportedException e) {
        return construir(HttpStatus.METHOD_NOT_ALLOWED, MensajesCompartidos.TITULO_ERROR_SOLICITUD,
                MensajesCompartidos.METODO_NO_PERMITIDO);
    }

    // ---------------- Base de datos ----------------

    /** Una restricción de la BD (CHECK, FK, RLS WITH CHECK, trigger) frenó la operación. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> integridad(DataIntegrityViolationException e) {
        LOG.warn(MensajesCompartidos.DATOS_INCONSISTENTES.clave(), e);
        return construir(REGLA_NEGOCIO_422, MensajesCompartidos.TITULO_REGLA_NEGOCIO,
                MensajesCompartidos.DATOS_INCONSISTENTES);
    }

    /** IMP25-02: si la base se cae se informa una falla temporal en vez de colapsar. */
    @ExceptionHandler({DataAccessResourceFailureException.class, TransientDataAccessException.class,
            CannotCreateTransactionException.class})
    ResponseEntity<ProblemDetail> baseNoDisponible(RuntimeException e) {
        LOG.error(MensajesCompartidos.BASE_DATOS_NO_DISPONIBLE.clave(), e);
        return construir(HttpStatus.SERVICE_UNAVAILABLE, MensajesCompartidos.TITULO_NO_DISPONIBLE,
                MensajesCompartidos.BASE_DATOS_NO_DISPONIBLE);
    }

    // ---------------- Cualquier otra cosa ----------------

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> inesperado(Exception e) {
        LOG.error(MensajesCompartidos.ERROR_INESPERADO.clave(), e);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, MensajesCompartidos.TITULO_ERROR_INTERNO,
                MensajesCompartidos.ERROR_INESPERADO);
    }

    // ---------------- Ayudas ----------------

    private ResponseEntity<ProblemDetail> desdeExcepcion(HttpStatus estado, CodigoMensaje titulo,
                                                         ExcepcionAplicacion e) {
        LOG.debug(e.getCodigo().clave());
        return construir(estado, titulo, e.getCodigo(), e.getArgumentos());
    }

    private ResponseEntity<ProblemDetail> construir(HttpStatus estado, CodigoMensaje titulo,
                                                    CodigoMensaje detalle, Object... argumentos) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, mensajes.obtener(detalle, argumentos));
        problema.setTitle(mensajes.obtener(titulo));
        problema.setProperty("codigo", detalle.clave());
        problema.setProperty("instante", Instant.now(reloj));
        return ResponseEntity.status(estado).body(problema);
    }
}
