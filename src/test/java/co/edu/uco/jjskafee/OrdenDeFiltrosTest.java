package co.edu.uco.jjskafee;

import co.edu.uco.jjskafee.acceso.dominio.Identidad;
import co.edu.uco.jjskafee.acceso.infraestructura.input.seguridad.FirebaseAuthFilter;
import co.edu.uco.jjskafee.acceso.servicios.input.ports.AutenticarUsuarioUseCase;
import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.NoAutenticadoException;
import co.edu.uco.jjskafee.compartido.infraestructura.input.seguridad.OrigenWafFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServlet;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OrigenWafFilter corre antes que FirebaseAuthFilter (ADR-0018). Los filtros se ordenan con el mismo
 * comparador que usa Spring Boot al registrarlos (@Order), así la prueba no depende del orden en que se escriben.
 */
class OrdenDeFiltrosTest {

    private static final String SECRETO = "secreto-de-prueba-largo-0123456789";

    /** Ningún token es válido: si FirebaseAuthFilter llega a correr, responde 401. */
    private final FirebaseAuthFilter autenticacion = new FirebaseAuthFilter(new AutenticarUsuarioUseCase() {
        @Override
        public Identidad identificar(String token) {
            throw new NoAutenticadoException("El token no es válido o ya venció");
        }

        @Override
        public UsuarioAutenticado autorizarEnCafeteria(Identidad identidad, UUID idCafeteria) {
            throw new AssertionError("no debe llegar aquí");
        }
    });
    private final OrigenWafFilter origen = new OrigenWafFilter(SECRETO);

    private List<Filter> filtrosEnElOrdenDeSpring() {
        List<Filter> filtros = new ArrayList<>(List.of(autenticacion, origen));
        AnnotationAwareOrderComparator.sort(filtros);
        return filtros;
    }

    private MockHttpServletResponse ejecutar(MockHttpServletRequest peticion) throws Exception {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        new MockFilterChain(new HttpServlet() { }, filtrosEnElOrdenDeSpring().toArray(Filter[]::new))
                .doFilter(peticion, respuesta);
        return respuesta;
    }

    @Test
    void elFiltroDeOrigenVaAntesQueElDeAutenticacion() {
        assertEquals(List.of(origen, autenticacion), filtrosEnElOrdenDeSpring());
    }

    @Test
    void sinEncabezadoDeOrigenNiTokenResponde403No401() throws Exception {
        assertEquals(403, ejecutar(new MockHttpServletRequest("GET", "/movimientos")).getStatus());
    }

    @Test
    void conElEncabezadoDeOrigenCorrectoSigueYLaAutenticacionPideElToken() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/movimientos");
        peticion.addHeader("X-JJSKaffee-Origen", SECRETO);

        assertEquals(401, ejecutar(peticion).getStatus());
    }
}
