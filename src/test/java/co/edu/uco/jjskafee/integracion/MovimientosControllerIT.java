package co.edu.uco.jjskafee.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static co.edu.uco.jjskafee.DatosPrueba.*;
import static co.edu.uco.jjskafee.integracion.ConfiguracionDePrueba.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La API completa por HTTP: filtro de token → controller → caso de uso → PostgreSQL. Todo se deshace al final. */
@PruebaDeIntegracion
@AutoConfigureMockMvc
@Transactional
class MovimientosControllerIT {

    @Autowired private MockMvc mvc;

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String egreso(Object idCategoria, String monto) {
        return """
                {"idCategoria": "%s", "monto": %s, "descripcion": "Compra de leche", "comprobante": "FAC-123"}"""
                .formatted(idCategoria, monto);
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mvc.perform(get("/movimientos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(post("/egresos").header("Authorization", bearer("inventado"))
                        .contentType(MediaType.APPLICATION_JSON).content(egreso(INSUMOS.getId(), "1000")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elAdministradorRegistraUnEgreso() throws Exception {
        mvc.perform(post("/egresos").header("Authorization", bearer(TOKEN_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(egreso(INSUMOS.getId(), "38000")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idEgreso").isNotEmpty())
                .andExpect(jsonPath("$.categoria").value("Insumos"))
                .andExpect(jsonPath("$.monto").value(38000))
                .andExpect(jsonPath("$.registradoPor").value(ADMIN.id().toString()))
                .andExpect(jsonPath("$.fechaHora").value(AHORA_PRUEBA.toString()));
    }

    @Test
    void elCajeroNoPuedeRegistrarEgresos() throws Exception {
        mvc.perform(post("/egresos").header("Authorization", bearer(TOKEN_CAJERO))
                        .contentType(MediaType.APPLICATION_JSON).content(egreso(INSUMOS.getId(), "38000")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Solo el administrador puede registrar egresos"));
    }

    @Test
    void unEgresoConDatosInvalidosResponde400ConLosCampos() throws Exception {
        mvc.perform(post("/egresos").header("Authorization", bearer(TOKEN_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idCategoria": null, "monto": -5, "descripcion": ""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.idCategoria").value("Elige una categoría"))
                .andExpect(jsonPath("$.errores.monto").value("Debe ser mayor que 0"))
                .andExpect(jsonPath("$.errores.descripcion").exists());
    }

    @Test
    void unaCategoriaArchivadaResponde422() throws Exception {
        mvc.perform(post("/egresos").header("Authorization", bearer(TOKEN_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(egreso(ARCHIVADA.getId(), "1000")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("La categoría debe ser de egreso y estar activa"));
    }

    @Test
    void laCategoriaDeOtraCafeteriaNoExiste() throws Exception {
        mvc.perform(post("/egresos").header("Authorization", bearer(TOKEN_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(egreso(INSUMOS_B.getId(), "1000")))
                .andExpect(status().isNotFound());
    }

    @Test
    void elTotalDeLaVentaLoCalculaElServidorEIgnoraElDelNavegador() throws Exception {
        mvc.perform(post("/ingresos").header("Authorization", bearer(TOKEN_CAJERO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"total": 1,
                                 "idCafeteria": "%s",
                                 "items": [{"idProducto": "%s", "cantidad": 1,
                                            "adiciones": [{"idProducto": "%s"}]}]}"""
                                .formatted(CAFETERIA_B, LATTE.getId(), SHOT.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(8500))
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.registradoPor").value(CAJERO.id().toString()))
                .andExpect(jsonPath("$.items[0].adiciones[0].nombre").value("Shot extra de espresso"));
    }

    @Test
    void quienVendioPuedeDeshacerLaVenta() throws Exception {
        MvcResult venta = mvc.perform(post("/ingresos").header("Authorization", bearer(TOKEN_CAJERO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [{"idProducto": "%s", "cantidad": 2}]}""".formatted(PANDEBONO.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        String idVenta = venta.getResponse().getContentAsString().replaceAll(".*\"idVenta\":\"([^\"]+)\".*", "$1");

        mvc.perform(post("/ingresos/{id}/deshacer", idVenta).header("Authorization", bearer(TOKEN_ADMIN)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/ingresos/{id}/deshacer", idVenta).header("Authorization", bearer(TOKEN_CAJERO_B)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/ingresos/{id}/deshacer", idVenta).header("Authorization", bearer(TOKEN_CAJERO)))
                .andExpect(status().isNoContent());
    }

    // ----- Validaciones de registrar ingreso -----

    private static String pandebonos(String cantidad) {
        return """
                {"items": [{"idProducto": "%s", "cantidad": %s}]}""".formatted(PANDEBONO.getId(), cantidad);
    }

    private String idVentaDe(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString().replaceAll(".*\"idVenta\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void laMismaIdempotencyKeyDevuelveLaMismaVenta() throws Exception {
        String clave = UUID.randomUUID().toString();

        MvcResult primera = mvc.perform(post("/ingresos").header("Authorization", bearer(TOKEN_CAJERO))
                        .header("Idempotency-Key", clave)
                        .contentType(MediaType.APPLICATION_JSON).content(pandebonos("2")))
                .andExpect(status().isCreated())
                .andReturn();
        mvc.perform(post("/ingresos").header("Authorization", bearer(TOKEN_CAJERO))
                        .header("Idempotency-Key", clave)
                        .contentType(MediaType.APPLICATION_JSON).content(pandebonos("2")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idVenta").value(idVentaDe(primera)))
                .andExpect(jsonPath("$.total").value(5000));
    }

    @Test
    void laMismaIdempotencyKeyConOtroCuerpoResponde422() throws Exception {
        String clave = UUID.randomUUID().toString();
        mvc.perform(post("/ingresos").header("Authorization", bearer(TOKEN_CAJERO))
                        .header("Idempotency-Key", clave)
                        .contentType(MediaType.APPLICATION_JSON).content(pandebonos("1")))
                .andExpect(status().isCreated());

        mvc.perform(post("/ingresos").header("Authorization", bearer(TOKEN_CAJERO))
                        .header("Idempotency-Key", clave)
                        .contentType(MediaType.APPLICATION_JSON).content(pandebonos("3")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Esta Idempotency-Key ya se usó para una venta con otros "
                        + "productos o cantidades; para registrar una venta nueva usa una clave nueva"));
    }

    @Test
    void unaIdempotencyKeyQueNoEsUuidResponde400() throws Exception {
        mvc.perform(post("/ingresos").header("Authorization", bearer(TOKEN_CAJERO))
                        .header("Idempotency-Key", "no-es-un-uuid")
                        .contentType(MediaType.APPLICATION_JSON).content(pandebonos("1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unaCantidadMayorA99Responde400ConElCampo() throws Exception {
        mvc.perform(post("/ingresos").header("Authorization", bearer(TOKEN_CAJERO))
                        .contentType(MediaType.APPLICATION_JSON).content(pandebonos("100")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores['items[0].cantidad']").value("La cantidad máxima es 99"));
    }

    @Test
    void unaCantidadConDecimalesResponde400EnVezDeTruncarse() throws Exception {
        mvc.perform(post("/ingresos").header("Authorization", bearer(TOKEN_CAJERO))
                        .contentType(MediaType.APPLICATION_JSON).content(pandebonos("1.5")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unaCafeteriaInactivaResponde422() throws Exception {
        mvc.perform(post("/ingresos").header("Authorization", bearer(TOKEN_CAJERO_INACTIVA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [{"idProducto": "%s", "cantidad": 1}]}""".formatted(TINTO_INACTIVA.getId())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("La cafetería no está activa; no se pueden registrar ingresos"));
    }

    @Test
    void consultaMovimientosPaginadosConTotales() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/egresos").header("Authorization", bearer(TOKEN_ADMIN))
                            .contentType(MediaType.APPLICATION_JSON).content(egreso(INSUMOS.getId(), "1000")))
                    .andExpect(status().isCreated());
        }

        mvc.perform(get("/movimientos").header("Authorization", bearer(TOKEN_ADMIN))
                        .param("desde", DIA_PRUEBA.toString()).param("hasta", DIA_PRUEBA.toString())
                        .param("tipo", "EGRESO").param("pagina", "0").param("tamano", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movimientos.length()").value(2))
                .andExpect(jsonPath("$.totalMovimientos").value(3))
                .andExpect(jsonPath("$.totales.egresos").value(3000))
                .andExpect(jsonPath("$.totales.balance").value(-3000));

        // La cafetería B no ve nada de A
        mvc.perform(get("/movimientos").header("Authorization", bearer(TOKEN_ADMIN_B))
                        .param("desde", DIA_PRUEBA.toString()).param("hasta", DIA_PRUEBA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMovimientos").value(0));
    }

    @Test
    void filtrosInvalidosResponden400() throws Exception {
        mvc.perform(get("/movimientos").header("Authorization", bearer(TOKEN_ADMIN)).param("tamano", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.tamano").value("El tamaño máximo es 50"));
        mvc.perform(get("/movimientos").header("Authorization", bearer(TOKEN_ADMIN)).param("tipo", "OTRO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.tipo").value("Valor no válido"));
        mvc.perform(get("/movimientos").header("Authorization", bearer(TOKEN_ADMIN))
                        .param("desde", "2026-10-10").param("hasta", "2026-10-01"))
                .andExpect(status().isUnprocessableContent());
    }
}
