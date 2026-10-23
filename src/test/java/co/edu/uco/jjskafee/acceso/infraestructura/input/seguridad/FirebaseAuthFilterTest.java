package co.edu.uco.jjskafee.acceso.infraestructura.input.seguridad;

import co.edu.uco.jjskafee.acceso.dominio.Identidad;
import co.edu.uco.jjskafee.acceso.servicios.input.ports.AutenticarUsuarioUseCase;
import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.NoAutenticadoException;
import co.edu.uco.jjskafee.compartido.dominio.excepciones.SinPermisoException;
import co.edu.uco.jjskafee.compartido.infraestructura.input.AtributosDePeticion;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static co.edu.uco.jjskafee.DatosPrueba.ADMIN;
import static co.edu.uco.jjskafee.DatosPrueba.CAFETERIA;
import static co.edu.uco.jjskafee.DatosPrueba.CAFETERIA_B;
import static org.junit.jupiter.api.Assertions.*;

class FirebaseAuthFilterTest {

    /** Caso de uso falso: solo acepta el token "valido", y ese usuario solo pertenece a la cafetería A. */
    private final FirebaseAuthFilter filtro = new FirebaseAuthFilter(new AutenticarUsuarioUseCase() {
        @Override
        public Identidad identificar(String token) {
            if (!"valido".equals(token)) {
                throw new NoAutenticadoException("El token no es válido o ya venció");
            }
            return new Identidad("uid-valido");
        }

        @Override
        public UsuarioAutenticado autorizarEnCafeteria(Identidad identidad, UUID idCafeteria) {
            if (!CAFETERIA.equals(idCafeteria)) {
                throw new SinPermisoException("No tienes acceso a esta cafetería");
            }
            return ADMIN;
        }
    });

    private MockHttpServletResponse ejecutar(MockHttpServletRequest peticion, MockFilterChain cadena) throws Exception {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        filtro.doFilter(peticion, respuesta, cadena);
        return respuesta;
    }

    private static MockHttpServletRequest peticion(String metodo, String ruta, String token, Object cafeteria) {
        MockHttpServletRequest peticion = new MockHttpServletRequest(metodo, ruta);
        if (token != null) {
            peticion.addHeader("Authorization", "Bearer " + token);
        }
        if (cafeteria != null) {
            peticion.addHeader("X-Cafeteria-Id", cafeteria.toString());
        }
        return peticion;
    }

    @Test
    void conTokenYCafeteriaDejaElUsuarioEnLaPeticionYSigue() throws Exception {
        MockHttpServletRequest peticion = peticion("POST", "/egresos", "valido", CAFETERIA);
        MockFilterChain cadena = new MockFilterChain();

        MockHttpServletResponse respuesta = ejecutar(peticion, cadena);

        assertEquals(200, respuesta.getStatus());
        assertSame(ADMIN, peticion.getAttribute(AtributosDePeticion.USUARIO));
        assertNotNull(cadena.getRequest());   // llegó al controller
    }

    @Test
    void sinEncabezadoResponde401YNoSigue() throws Exception {
        MockFilterChain cadena = new MockFilterChain();
        MockHttpServletResponse respuesta = ejecutar(peticion("GET", "/movimientos", null, CAFETERIA), cadena);

        assertEquals(401, respuesta.getStatus());
        assertEquals("Bearer", respuesta.getHeader("WWW-Authenticate"));
        assertTrue(respuesta.getContentAsString().contains("Falta el token"));
        assertNull(cadena.getRequest());
    }

    @Test
    void conTokenInvalidoResponde401() throws Exception {
        MockFilterChain cadena = new MockFilterChain();

        assertEquals(401, ejecutar(peticion("GET", "/movimientos", "inventado", CAFETERIA), cadena).getStatus());
        assertNull(cadena.getRequest());
    }

    @Test
    void otroEsquemaDeAutorizacionNoSirve() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/movimientos");
        peticion.addHeader("Authorization", "Basic dXN1YXJpbzpjbGF2ZQ==");
        peticion.addHeader("X-Cafeteria-Id", CAFETERIA.toString());

        assertEquals(401, ejecutar(peticion, new MockFilterChain()).getStatus());
    }

    @Test
    void sinTokenNiCafeteriaPrimeroResponde401() throws Exception {
        assertEquals(401, ejecutar(peticion("GET", "/movimientos", null, null), new MockFilterChain()).getStatus());
    }

    @Test
    void sinCafeteriaResponde400YNoSigue() throws Exception {
        MockFilterChain cadena = new MockFilterChain();
        MockHttpServletResponse respuesta = ejecutar(peticion("GET", "/movimientos", "valido", null), cadena);

        assertEquals(400, respuesta.getStatus());
        assertTrue(respuesta.getContentType().startsWith("application/problem+json"), respuesta.getContentType());
        assertTrue(respuesta.getContentAsString().contains("Falta el encabezado X-Cafeteria-Id"));
        assertNull(cadena.getRequest());
    }

    @Test
    void unaCafeteriaQueNoEsUuidResponde400() throws Exception {
        MockFilterChain cadena = new MockFilterChain();
        MockHttpServletResponse respuesta = ejecutar(peticion("GET", "/movimientos", "valido", "cafeteria-a"), cadena);

        assertEquals(400, respuesta.getStatus());
        assertTrue(respuesta.getContentAsString().contains("debe ser un UUID"));
        assertNull(cadena.getRequest());
    }

    @Test
    void sinMembresiaEnLaCafeteriaResponde403YNoSigue() throws Exception {
        MockFilterChain cadena = new MockFilterChain();
        MockHttpServletResponse respuesta = ejecutar(peticion("POST", "/egresos", "valido", CAFETERIA_B), cadena);

        assertEquals(403, respuesta.getStatus());
        assertTrue(respuesta.getContentAsString().contains("\"status\":403"));
        assertTrue(respuesta.getContentAsString().contains("No tienes acceso a esta cafetería"));
        assertNull(cadena.getRequest());
    }

    @Test
    void misCafeteriasSoloPideElTokenYDejaLaIdentidad() throws Exception {
        MockHttpServletRequest peticion = peticion("GET", "/acceso/cafeterias", "valido", null);
        MockFilterChain cadena = new MockFilterChain();

        assertEquals(200, ejecutar(peticion, cadena).getStatus());
        assertEquals(new Identidad("uid-valido"), peticion.getAttribute(AtributosDePeticion.IDENTIDAD));
        assertNull(peticion.getAttribute(AtributosDePeticion.USUARIO));
        assertNotNull(cadena.getRequest());
    }

    @Test
    void misCafeteriasSinTokenResponde401() throws Exception {
        assertEquals(401, ejecutar(peticion("GET", "/acceso/cafeterias", null, null), new MockFilterChain()).getStatus());
    }

    @Test
    void elPreflightDeCorsNoPideToken() throws Exception {
        MockFilterChain cadena = new MockFilterChain();
        MockHttpServletResponse respuesta = ejecutar(new MockHttpServletRequest("OPTIONS", "/egresos"), cadena);

        assertEquals(200, respuesta.getStatus());
        assertNotNull(cadena.getRequest());
    }
}
