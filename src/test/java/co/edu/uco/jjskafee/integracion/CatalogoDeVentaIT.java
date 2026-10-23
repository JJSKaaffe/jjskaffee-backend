package co.edu.uco.jjskafee.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static co.edu.uco.jjskafee.DatosPrueba.*;
import static co.edu.uco.jjskafee.integracion.ConfiguracionDePrueba.*;
import static co.edu.uco.jjskafee.integracion.NumeroJson.numero;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /ingresos/catalogo contra PostgreSQL (seeds db/03 y db/10): lo que muestra la pantalla de venta. */
@PruebaDeIntegracion
@AutoConfigureMockMvc
@Transactional
class CatalogoDeVentaIT {

    @Autowired private MockMvc mvc;

    private static String producto(Object id) {
        return "$.productos[?(@.idProducto == '" + id + "')]";
    }

    @Test
    void elAdministradorVeLosProductosBaseConSusAdicionesYPrecios() throws Exception {
        mvc.perform(get("/ingresos/catalogo").headers(sesion(TOKEN_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(producto(LATTE.getId()) + ".nombre").value("Café latte"))
                .andExpect(jsonPath(producto(LATTE.getId()) + ".precio", contains(numero("6500"))))
                .andExpect(jsonPath(producto(LATTE.getId()) + ".adiciones[*].nombre",
                        contains("Leche de almendra", "Shot extra de espresso")))
                .andExpect(jsonPath(producto(LATTE.getId()) + ".adiciones[*].precio", contains(numero("1500"), numero("2000"))))
                .andExpect(jsonPath(producto(ESPRESSO.getId()) + ".adiciones[*].idProducto",
                        contains(SHOT.getId().toString())))
                .andExpect(jsonPath(producto(PANDEBONO.getId()) + ".adiciones[*]").value(empty()))
                // Las adiciones no se venden solas: no aparecen como productos
                .andExpect(jsonPath("$.productos[*].idProducto",
                        not(hasItems(ALMENDRA.getId().toString(), SHOT.getId().toString()))))
                // Ni productos de otra cafetería
                .andExpect(jsonPath("$.productos[*].idProducto", not(hasItem(CAPUCHINO_B.getId().toString()))));
    }

    @Test
    void elAdministradorVeLasCategoriasDeIngresoActivas() throws Exception {
        mvc.perform(get("/ingresos/catalogo").headers(sesion(TOKEN_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoriasIngreso[*].nombre",
                        containsInAnyOrder("Eventos privados", "Venta de café en grano")))
                .andExpect(jsonPath("$.categoriasIngreso[*].idCategoria", hasItem(EVENTOS.getId().toString())))
                // Ni la archivada ni las de egreso o menú
                .andExpect(jsonPath("$.categoriasIngreso[*].idCategoria", not(hasItems(
                        CONVENIOS_ARCHIVADA.getId().toString(), INSUMOS.getId().toString(), BEBIDAS.getId().toString()))));
    }

    @Test
    void elCajeroVeLosProductosPeroNoLasCategoriasDeIngreso() throws Exception {
        mvc.perform(get("/ingresos/catalogo").headers(sesion(TOKEN_CAJERO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(producto(LATTE.getId()) + ".nombre").value("Café latte"))
                .andExpect(jsonPath("$.categoriasIngreso").value(empty()));
    }

    @Test
    void cadaCafeteriaVeSoloSuCatalogo() throws Exception {
        mvc.perform(get("/ingresos/catalogo").headers(sesion(TOKEN_ADMIN_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productos[*].idProducto", contains(CAPUCHINO_B.getId().toString())))
                .andExpect(jsonPath("$.categoriasIngreso").value(empty()));
    }

    @Test
    void sinTokenOSinCafeteriaNoHayCatalogo() throws Exception {
        mvc.perform(get("/ingresos/catalogo"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/ingresos/catalogo").header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/ingresos/catalogo").headers(sesion(TOKEN_CAJERO, CAFETERIA_B)))
                .andExpect(status().isForbidden());
    }
}
