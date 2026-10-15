package co.edu.uco.jjskafee.compartido.infraestructura.seguridad;

import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.NoAutenticadoException;
import co.edu.uco.jjskafee.compartido.dominio.mensajes.MensajesCompartidos;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Permite que un controlador reciba el usuario como parámetro:
 *   public IngresoResponse registrar(@RequestBody ... , UsuarioAutenticado usuario)
 * El usuario sale del token (FiltroAutenticacion), nunca del cuerpo de la petición.
 */
public class ResolutorUsuarioAutenticado implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parametro) {
        return UsuarioAutenticado.class.equals(parametro.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parametro, ModelAndViewContainer contenedor,
                                  NativeWebRequest peticion, WebDataBinderFactory fabrica) {
        Object usuario = peticion.getAttribute(FiltroAutenticacion.ATRIBUTO_USUARIO, RequestAttributes.SCOPE_REQUEST);
        if (usuario instanceof UsuarioAutenticado autenticado) {
            return autenticado;
        }
        throw new NoAutenticadoException(MensajesCompartidos.AUTENTICACION_REQUERIDA);
    }
}
