package co.edu.uco.jjskafee.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static co.edu.uco.jjskafee.DatosPrueba.*;
import static co.edu.uco.jjskafee.integracion.ConfiguracionDePrueba.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login → elegir cafetería → rol (ADR-0016), por HTTP completo: filtro → controller → caso de uso → PostgreSQL.
 * El token solo identifica a la persona; la cafetería va en X-Cafeteria-Id y el rol sale de la membresía.
 */
@PruebaDeIntegracion
@AutoConfigureMockMvc
@Transactional
class AccesoControllerIT {

    @Autowired private MockMvc mvc;

    private static String egreso(UUID idCategoria) {
        return """
                {"idCategoria": "%s", "monto": 1000, "descripcion": "Compra de leche"}""".formatted(idCategoria);
    }

    // ----- GET /acceso/cafeterias: solo con el token -----

    @Test
    void lauraVeSusDosCafeteriasConUnRolDistintoEnCadaUna() throws Exception {
        // Ordenadas por nombre: "Café El Parque (B)" antes que "Café La Esquina (A)"
        mvc.perform(get("/acceso/cafeterias").header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].idCafeteria").value(CAFETERIA_B.toString()))
                .andExpect(jsonPath("$[0].nombre").value(NOMBRE_CAFETERIA_B))
                .andExpect(jsonPath("$[0].rol").value("CAJERO"))
                .andExpect(jsonPath("$[0].activa").value(true))
                .andExpect(jsonPath("$[1].idCafeteria").value(CAFETERIA.toString()))
                .andExpect(jsonPath("$[1].nombre").value(NOMBRE_CAFETERIA))
                .andExpect(jsonPath("$[1].rol").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$[1].activa").value(true));
    }

    @Test
    void misCafeteriasNoUsaElEncabezadoDeCafeteria() throws Exception {
        // Aunque llegue X-Cafeteria-Id (incluso inválido), la lista sale solo de la identidad
        mvc.perform(get("/acceso/cafeterias").header("Authorization", "Bearer " + TOKEN_CAJERO)
                        .header("X-Cafeteria-Id", "no-es-un-uuid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].idCafeteria").value(CAFETERIA.toString()))
                .andExpect(jsonPath("$[0].rol").value("CAJERO"));
    }

    @Test
    void unaCafeteriaInactivaApareceConActivaFalse() throws Exception {
        mvc.perform(get("/acceso/cafeterias").header("Authorization", "Bearer " + TOKEN_CAJERO_INACTIVA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].idCafeteria").value(CAFETERIA_INACTIVA.toString()))
                .andExpect(jsonPath("$[0].activa").value(false));
    }

    @Test
    void sinMembresiasLaListaEstaVacia() throws Exception {
        mvc.perform(get("/acceso/cafeterias").header("Authorization", "Bearer " + TOKEN_SIN_MEMBRESIA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void misCafeteriasSinTokenOConTokenInvalidoResponde401() throws Exception {
        mvc.perform(get("/acceso/cafeterias"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/acceso/cafeterias").header("Authorization", "Bearer inventado"))
                .andExpect(status().isUnauthorized());
    }

    // ----- GET /acceso/yo: rol y permisos en la cafetería elegida -----

    @Test
    void enLaCafeteriaALauraEsAdministradoraConTodosLosPermisos() throws Exception {
        mvc.perform(get("/acceso/yo").headers(sesion(TOKEN_ADMIN, CAFETERIA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ADMIN.id().toString()))
                .andExpect(jsonPath("$.nombre").value("Laura Gómez"))
                .andExpect(jsonPath("$.rol").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.idCafeteria").value(CAFETERIA.toString()))
                .andExpect(jsonPath("$.permisos.length()").value(4))
                .andExpect(jsonPath("$.permisos[0]").value("REGISTRAR_VENTA"))
                .andExpect(jsonPath("$.permisos[1]").value("REGISTRAR_INGRESO_LIBRE"))
                .andExpect(jsonPath("$.permisos[2]").value("REGISTRAR_EGRESO"))
                .andExpect(jsonPath("$.permisos[3]").value("VER_TODOS_LOS_MOVIMIENTOS"));
    }

    @Test
    void enLaCafeteriaBLauraEsCajeraYSoloRegistraVentas() throws Exception {
        mvc.perform(get("/acceso/yo").headers(sesion(TOKEN_ADMIN, CAFETERIA_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(LAURA_EN_B.id().toString()))
                .andExpect(jsonPath("$.rol").value("CAJERO"))
                .andExpect(jsonPath("$.idCafeteria").value(CAFETERIA_B.toString()))
                .andExpect(jsonPath("$.permisos.length()").value(1))
                .andExpect(jsonPath("$.permisos[0]").value("REGISTRAR_VENTA"));
    }

    // ----- El mismo token, distinto rol según la cafetería -----

    @Test
    void conElMismoTokenEnAPuedeRegistrarEgresosYEnBRecibe403() throws Exception {
        mvc.perform(post("/egresos").headers(sesion(TOKEN_ADMIN, CAFETERIA))
                        .contentType(MediaType.APPLICATION_JSON).content(egreso(INSUMOS.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registradoPor").value(ADMIN.id().toString()));

        mvc.perform(post("/egresos").headers(sesion(TOKEN_ADMIN, CAFETERIA_B))
                        .contentType(MediaType.APPLICATION_JSON).content(egreso(INSUMOS_B.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Solo el administrador puede registrar egresos"));
    }

    @Test
    void unaVentaEnBQuedaRegistradaConLaMembresiaDeB() throws Exception {
        mvc.perform(post("/ingresos").headers(sesion(TOKEN_ADMIN, CAFETERIA_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [{"idProducto": "%s", "cantidad": 1}]}""".formatted(CAPUCHINO_B.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registradoPor").value(LAURA_EN_B.id().toString()))
                .andExpect(jsonPath("$.total").value(7000));
    }

    // ----- Respuestas de acceso: 401, 400 y 403 -----

    @Test
    void unUidSinMembresiaRecibe403() throws Exception {
        mvc.perform(get("/movimientos").headers(sesion(TOKEN_SIN_MEMBRESIA, CAFETERIA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Sin permiso"))
                .andExpect(jsonPath("$.detail").value("No tienes acceso a esta cafetería"));
        mvc.perform(get("/acceso/yo").headers(sesion(TOKEN_SIN_MEMBRESIA, CAFETERIA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void nadieEntraAUnaCafeteriaDondeNoTieneMembresia() throws Exception {
        // Andrés solo pertenece a A
        mvc.perform(get("/movimientos").headers(sesion(TOKEN_CAJERO, CAFETERIA_B)))
                .andExpect(status().isForbidden());
        // Una cafetería que no existe responde igual: no se revela cuáles existen
        mvc.perform(get("/movimientos").headers(sesion(TOKEN_ADMIN, UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No tienes acceso a esta cafetería"));
    }

    @Test
    void sinElEncabezadoDeCafeteriaResponde400() throws Exception {
        mvc.perform(get("/movimientos").header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Falta el encabezado X-Cafeteria-Id con la cafetería elegida"));
        mvc.perform(get("/acceso/yo").header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unEncabezadoDeCafeteriaQueNoEsUuidResponde400() throws Exception {
        mvc.perform(post("/ingresos").header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .header("X-Cafeteria-Id", "cafeteria-a")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El encabezado X-Cafeteria-Id debe ser un UUID"));
    }

    @Test
    void sinTokenResponde401AntesDeRevisarLaCafeteria() throws Exception {
        mvc.perform(get("/movimientos"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/movimientos").header("X-Cafeteria-Id", CAFETERIA.toString())
                        .header("Authorization", "Bearer inventado"))
                .andExpect(status().isUnauthorized());
    }
}
