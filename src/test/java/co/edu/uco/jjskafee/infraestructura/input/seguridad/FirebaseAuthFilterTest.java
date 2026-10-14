package co.edu.uco.jjskafee.infraestructura.input.seguridad;

import co.edu.uco.jjskafee.dominio.excepciones.NoAutenticadoException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static co.edu.uco.jjskafee.DatosPrueba.ADMIN;
import static org.junit.jupiter.api.Assertions.*;

class FirebaseAuthFilterTest {

    /** Verificador falso: solo acepta el token "valido". */
    private final FirebaseAuthFilter filtro = new FirebaseAuthFilter(token -> {
        if (!"valido".equals(token)) {
            throw new NoAutenticadoException("El token no es válido o ya venció");
        }
        return ADMIN;
    });

    private MockHttpServletResponse ejecutar(MockHttpServletRequest peticion, MockFilterChain cadena) throws Exception {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        filtro.doFilter(peticion, respuesta, cadena);
        return respuesta;
    }

    @Test
    void conTokenValidoDejaElUsuarioEnLaPeticionYSigue() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("POST", "/egresos");
        peticion.addHeader("Authorization", "Bearer valido");
        MockFilterChain cadena = new MockFilterChain();

        MockHttpServletResponse respuesta = ejecutar(peticion, cadena);

        assertEquals(200, respuesta.getStatus());
        assertSame(ADMIN, peticion.getAttribute(FirebaseAuthFilter.USUARIO));
        assertNotNull(cadena.getRequest());   // llegó al controller
    }

    @Test
    void sinEncabezadoResponde401YNoSigue() throws Exception {
        MockFilterChain cadena = new MockFilterChain();
        MockHttpServletResponse respuesta = ejecutar(new MockHttpServletRequest("GET", "/movimientos"), cadena);

        assertEquals(401, respuesta.getStatus());
        assertEquals("Bearer", respuesta.getHeader("WWW-Authenticate"));
        assertTrue(respuesta.getContentAsString().contains("Falta el token"));
        assertNull(cadena.getRequest());
    }

    @Test
    void conTokenInvalidoResponde401() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/movimientos");
        peticion.addHeader("Authorization", "Bearer inventado");
        MockFilterChain cadena = new MockFilterChain();

        assertEquals(401, ejecutar(peticion, cadena).getStatus());
        assertNull(cadena.getRequest());
    }

    @Test
    void otroEsquemaDeAutorizacionNoSirve() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/movimientos");
        peticion.addHeader("Authorization", "Basic dXN1YXJpbzpjbGF2ZQ==");

        assertEquals(401, ejecutar(peticion, new MockFilterChain()).getStatus());
    }

    @Test
    void elPreflightDeCorsNoPideToken() throws Exception {
        MockFilterChain cadena = new MockFilterChain();
        MockHttpServletResponse respuesta = ejecutar(new MockHttpServletRequest("OPTIONS", "/egresos"), cadena);

        assertEquals(200, respuesta.getStatus());
        assertNotNull(cadena.getRequest());
    }
}
