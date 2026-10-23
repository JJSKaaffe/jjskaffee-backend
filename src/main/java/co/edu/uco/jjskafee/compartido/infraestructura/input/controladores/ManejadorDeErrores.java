package co.edu.uco.jjskafee.compartido.infraestructura.input.controladores;

import co.edu.uco.jjskafee.compartido.dominio.excepciones.NoAutenticadoException;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.RecursoNoEncontradoException;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.ReglaNegocioException;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.SinPermisoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Convierte las excepciones en respuestas ProblemDetail (RFC 9457) con mensajes en español.
 * Los errores inesperados responden 500 sin mostrar detalles internos (SQL, trazas).
 */
@RestControllerAdvice
public class ManejadorDeErrores extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorDeErrores.class);

    @ExceptionHandler(ReglaNegocioException.class)
    public ProblemDetail reglaDeNegocio(ReglaNegocioException e) {
        return problema(HttpStatus.UNPROCESSABLE_CONTENT, "Regla de negocio", e.getMessage());
    }

    @ExceptionHandler(SinPermisoException.class)
    public ProblemDetail sinPermiso(SinPermisoException e) {
        return problema(HttpStatus.FORBIDDEN, "Sin permiso", e.getMessage());
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail noEncontrado(RecursoNoEncontradoException e) {
        return problema(HttpStatus.NOT_FOUND, "No encontrado", e.getMessage());
    }

    @ExceptionHandler(NoAutenticadoException.class)
    public ProblemDetail noAutenticado(NoAutenticadoException e) {
        return problema(HttpStatus.UNAUTHORIZED, "No autenticado", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail inesperado(Exception e) {
        LOG.error("Error inesperado atendiendo la petición", e);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", "Ocurrió un error inesperado. Intenta de nuevo.");
    }

    /** 400 con el mensaje de cada campo inválido, para que el frontend lo muestre junto al campo. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        Map<String, String> errores = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> errores.putIfAbsent(error.getField(),
                        error.isBindingFailure() ? "Valor no válido" : error.getDefaultMessage()));
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Datos inválidos", "Revisa los campos marcados");
        problema.setProperty("errores", errores);
        return ResponseEntity.badRequest().body(problema);
    }

    private static ProblemDetail problema(HttpStatus estado, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        return problema;
    }
}
