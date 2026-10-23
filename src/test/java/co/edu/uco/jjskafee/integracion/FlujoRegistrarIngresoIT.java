package co.edu.uco.jjskafee.integracion;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static co.edu.uco.jjskafee.DatosPrueba.*;
import static co.edu.uco.jjskafee.integracion.ConfiguracionDePrueba.*;
import static co.edu.uco.jjskafee.integracion.NumeroJson.numero;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El flujo completo de "registrar ingreso", por HTTP y contra PostgreSQL, como lo hará el frontend:
 * login (token) → ver sus cafeterías y elegir una → saber su rol → registrar el ingreso → verlo reflejado
 * en los movimientos con los totales. Todo se deshace al final de cada prueba.
 */
@PruebaDeIntegracion
@AutoConfigureMockMvc
@Transactional
class FlujoRegistrarIngresoIT {

    @Autowired private MockMvc mvc;

    /** Paso 1–2: con solo el token, la persona ve sus cafeterías y elige la que tenga ese rol. */
    private UUID elegirCafeteria(String token, String rol) throws Exception {
        String cafeterias = mvc.perform(get("/acceso/cafeterias").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(cafeterias, "$[?(@.rol == '" + rol + "')].idCafeteria");
        return UUID.fromString(ids.getFirst());
    }

    private String registrarVenta(HttpHeaders sesion, String cuerpo, String totalEsperado) throws Exception {
        String respuesta = mvc.perform(post("/ingresos").headers(sesion)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(Integer.parseInt(totalEsperado)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.idVenta");
    }

    private String registrarIngresoLibre(HttpHeaders sesion, UUID idCategoria, String monto) throws Exception {
        String respuesta = mvc.perform(post("/ingresos/libres").headers(sesion)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idCategoria": "%s", "monto": %s, "descripcion": "Cumpleaños de 20 personas"}"""
                                .formatted(idCategoria, monto)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.idIngreso");
    }

    private static String movimiento(String id) {
        return "$.movimientos[?(@.idMovimiento == '" + id + "')]";
    }

    @Test
    void lauraEligeLaCafeteriaARegistraUnaVentaYUnIngresoLibreYLosVeConLosTotales() throws Exception {
        // 1–2. Login y elegir cafetería: Laura es ADMINISTRADORA en A (y cajera en B)
        UUID idCafeteria = elegirCafeteria(TOKEN_ADMIN, "ADMINISTRADOR");
        assertEquals(CAFETERIA, idCafeteria);
        HttpHeaders laura = sesion(TOKEN_ADMIN, idCafeteria);

        // 3. El sistema sabe su rol en esa cafetería: el frontend arma el menú con los permisos
        mvc.perform(get("/acceso/yo").headers(laura))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.permisos", hasItem("REGISTRAR_INGRESO_LIBRE")));

        // La pantalla de venta: productos con adiciones y categorías de ingreso
        mvc.perform(get("/ingresos/catalogo").headers(laura))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productos[*].idProducto", hasItem(LATTE.getId().toString())))
                .andExpect(jsonPath("$.categoriasIngreso[*].idCategoria", hasItem(EVENTOS.getId().toString())));

        // 4. Registra una venta (latte 6500 + shot 2000 = 8500, calculado en el servidor) y un ingreso libre
        String idVenta = registrarVenta(laura, """
                {"items": [{"idProducto": "%s", "cantidad": 1, "adiciones": [{"idProducto": "%s"}]}]}"""
                .formatted(LATTE.getId(), SHOT.getId()), "8500");
        String idIngresoLibre = registrarIngresoLibre(laura, EVENTOS.getId(), "150000");

        // 5. Verlo reflejado: los dos como INGRESO, con el total correcto
        mvc.perform(get("/movimientos").headers(laura)
                        .param("desde", DIA_PRUEBA.toString()).param("hasta", DIA_PRUEBA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMovimientos").value(2))
                .andExpect(jsonPath("$.totales.ingresos").value(158500))
                .andExpect(jsonPath("$.totales.egresos").value(0))
                .andExpect(jsonPath("$.totales.balance").value(158500))
                .andExpect(jsonPath(movimiento(idVenta) + ".tipo").value("INGRESO"))
                .andExpect(jsonPath(movimiento(idVenta) + ".descripcion").value("Venta"))
                .andExpect(jsonPath(movimiento(idVenta) + ".monto", contains(numero("8500"))))
                .andExpect(jsonPath(movimiento(idIngresoLibre) + ".tipo").value("INGRESO"))
                .andExpect(jsonPath(movimiento(idIngresoLibre) + ".categoria").value("Eventos privados"))
                .andExpect(jsonPath(movimiento(idIngresoLibre) + ".monto", contains(numero("150000"))))
                .andExpect(jsonPath(movimiento(idIngresoLibre) + ".registradoPor").value("Laura Gómez"));
    }

    @Test
    void andresCajeroNoRegistraIngresosLibresYSoloVeLoQueElRegistro() throws Exception {
        // Laura (administradora) registra primero una venta y un ingreso libre en A
        HttpHeaders laura = sesion(TOKEN_ADMIN, CAFETERIA);
        String ventaDeLaura = registrarVenta(laura, """
                {"items": [{"idProducto": "%s", "cantidad": 1, "adiciones": [{"idProducto": "%s"}]}]}"""
                .formatted(LATTE.getId(), SHOT.getId()), "8500");
        String libreDeLaura = registrarIngresoLibre(laura, EVENTOS.getId(), "150000");

        // Andrés inicia sesión, ve que en A es CAJERO y la elige
        UUID idCafeteria = elegirCafeteria(TOKEN_CAJERO, "CAJERO");
        assertEquals(CAFETERIA, idCafeteria);
        HttpHeaders andres = sesion(TOKEN_CAJERO, idCafeteria);

        mvc.perform(get("/acceso/yo").headers(andres))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("CAJERO"))
                .andExpect(jsonPath("$.permisos", contains("REGISTRAR_VENTA")));
        mvc.perform(get("/ingresos/catalogo").headers(andres))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoriasIngreso").isEmpty());

        // No puede registrar ingresos libres
        mvc.perform(post("/ingresos/libres").headers(andres)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idCategoria": "%s", "monto": 1000, "descripcion": "Intento"}""".formatted(EVENTOS.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Solo el administrador puede registrar ingresos libres"));

        // Sí registra ventas
        String ventaDeAndres = registrarVenta(andres, """
                {"items": [{"idProducto": "%s", "cantidad": 2}]}""".formatted(PANDEBONO.getId()), "5000");

        // En movimientos solo ve lo suyo, y los totales son solo de lo suyo
        mvc.perform(get("/movimientos").headers(andres)
                        .param("desde", DIA_PRUEBA.toString()).param("hasta", DIA_PRUEBA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMovimientos").value(1))
                .andExpect(jsonPath("$.movimientos[0].idMovimiento").value(ventaDeAndres))
                .andExpect(jsonPath("$.movimientos[0].registradoPor").value("Andrés Ruiz"))
                .andExpect(jsonPath("$.totales.ingresos").value(5000))
                .andExpect(jsonPath("$.totales.balance").value(5000));

        // La administradora ve todo lo de la cafetería: 8500 + 150000 + 5000
        mvc.perform(get("/movimientos").headers(laura)
                        .param("desde", DIA_PRUEBA.toString()).param("hasta", DIA_PRUEBA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMovimientos").value(3))
                .andExpect(jsonPath("$.movimientos[*].idMovimiento",
                        containsInAnyOrder(ventaDeLaura, libreDeLaura, ventaDeAndres)))
                .andExpect(jsonPath("$.totales.ingresos").value(163500));
    }

    @Test
    void lauraComoCajeraEnBNoPuedeRegistrarIngresosLibresAunqueEnASi() throws Exception {
        UUID cafeteriaB = elegirCafeteria(TOKEN_ADMIN, "CAJERO");
        assertEquals(CAFETERIA_B, cafeteriaB);
        HttpHeaders lauraEnB = sesion(TOKEN_ADMIN, cafeteriaB);

        mvc.perform(post("/ingresos/libres").headers(lauraEnB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idCategoria": "%s", "monto": 1000, "descripcion": "Intento"}""".formatted(EVENTOS.getId())))
                .andExpect(status().isForbidden());

        // En B vende como cajera y solo ve lo suyo de B
        String venta = registrarVenta(lauraEnB, """
                {"items": [{"idProducto": "%s", "cantidad": 1}]}""".formatted(CAPUCHINO_B.getId()), "7000");
        mvc.perform(get("/movimientos").headers(lauraEnB)
                        .param("desde", DIA_PRUEBA.toString()).param("hasta", DIA_PRUEBA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMovimientos").value(1))
                .andExpect(jsonPath("$.movimientos[0].idMovimiento").value(venta))
                .andExpect(jsonPath("$.totales.ingresos").value(7000));
    }
}
