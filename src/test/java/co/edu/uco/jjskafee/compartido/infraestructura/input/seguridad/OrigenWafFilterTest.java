package co.edu.uco.jjskafee.compartido.infraestructura.input.seguridad;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class OrigenWafFilterTest {

    private static final String SECRETO = "secreto-de-prueba-largo-0123456789";

    private static MockHttpServletResponse ejecutar(OrigenWafFilter filtro, MockHttpServletRequest peticion,
                                                    MockFilterChain cadena) throws Exception {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        filtro.doFilter(peticion, respuesta, cadena);
        return respuesta;
    }

    private static MockHttpServletRequest peticion(String metodo, String origen) {
        MockHttpServletRequest peticion = new MockHttpServletRequest(metodo, "/movimientos");
        if (origen != null) {
            peticion.addHeader("X-JJSKaffee-Origen", origen);
        }
        return peticion;
    }

    @Test
    void sinSecretoConfiguradoDejaPasarSinElEncabezado() throws Exception {
        MockFilterChain cadena = new MockFilterChain();

        assertEquals(200, ejecutar(new OrigenWafFilter(""), peticion("GET", null), cadena).getStatus());
        assertNotNull(cadena.getRequest());
    }

    @Test
    void conSecretoYSinEncabezadoResponde403YNoSigue() throws Exception {
        MockFilterChain cadena = new MockFilterChain();
        MockHttpServletResponse respuesta = ejecutar(new OrigenWafFilter(SECRETO), peticion("GET", null), cadena);

        assertEquals(403, respuesta.getStatus());
        assertTrue(respuesta.getContentType().startsWith("application/problem+json"), respuesta.getContentType());
        assertTrue(respuesta.getContentAsString().contains("\"status\":403"));
        assertTrue(respuesta.getContentAsString().contains("Acceso no permitido"));
        assertNull(cadena.getRequest());
    }

    @Test
    void conValorIncorrectoResponde403ConElMismoMensaje() throws Exception {
        OrigenWafFilter filtro = new OrigenWafFilter(SECRETO);
        MockFilterChain cadena = new MockFilterChain();
        MockHttpServletResponse incorrecto = ejecutar(filtro, peticion("GET", "otro-valor"), cadena);
        MockHttpServletResponse sinEncabezado = ejecutar(filtro, peticion("GET", null), new MockFilterChain());

        assertEquals(403, incorrecto.getStatus());
        assertNull(cadena.getRequest());
        // No revela si faltó el encabezado o si no coincidió
        assertEquals(sinEncabezado.getContentAsString(), incorrecto.getContentAsString());
    }

    @Test
    void unPrefijoDelSecretoNoSirve() throws Exception {
        MockFilterChain cadena = new MockFilterChain();

        assertEquals(403, ejecutar(new OrigenWafFilter(SECRETO), peticion("GET", SECRETO.substring(0, 10)), cadena).getStatus());
        assertNull(cadena.getRequest());
    }

    @Test
    void conElValorCorrectoDejaPasar() throws Exception {
        MockFilterChain cadena = new MockFilterChain();

        assertEquals(200, ejecutar(new OrigenWafFilter(SECRETO), peticion("POST", SECRETO), cadena).getStatus());
        assertNotNull(cadena.getRequest());
    }

    @Test
    void elPreflightTambienExigeElEncabezado() throws Exception {
        // El balanceador lo agrega a todas las peticiones, OPTIONS incluido
        OrigenWafFilter filtro = new OrigenWafFilter(SECRETO);

        assertEquals(403, ejecutar(filtro, peticion("OPTIONS", null), new MockFilterChain()).getStatus());
        assertEquals(200, ejecutar(filtro, peticion("OPTIONS", SECRETO), new MockFilterChain()).getStatus());
    }

    @Test
    void ignoraEspaciosYSaltoDeLineaAlFinalDelSecretoConfigurado() throws Exception {
        // Un secreto copiado con salto de línea no debe dejar a todos afuera
        MockFilterChain cadena = new MockFilterChain();

        assertEquals(200, ejecutar(new OrigenWafFilter(SECRETO + "\n"), peticion("GET", SECRETO), cadena).getStatus());
        assertNotNull(cadena.getRequest());
    }
}
