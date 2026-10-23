package co.edu.uco.jjskafee.ingresos.servicios.casouso;

import co.edu.uco.jjskafee.RepositoriosEnMemoria;
import co.edu.uco.jjskafee.ingresos.servicios.input.dto.response.CatalogoDeVentaResponse;
import co.edu.uco.jjskafee.ingresos.servicios.input.dto.response.CatalogoDeVentaResponse.AdicionResponse;
import co.edu.uco.jjskafee.ingresos.servicios.input.dto.response.CatalogoDeVentaResponse.CategoriaResponse;
import co.edu.uco.jjskafee.ingresos.servicios.input.dto.response.CatalogoDeVentaResponse.ProductoResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static co.edu.uco.jjskafee.DatosPrueba.*;
import static org.junit.jupiter.api.Assertions.*;

class ConsultarCatalogoDeVentaTest {

    private final RepositoriosEnMemoria.CatalogoDeVenta catalogo = new RepositoriosEnMemoria.CatalogoDeVenta()
            .producto(LATTE, ALMENDRA, SHOT)
            .producto(PANDEBONO)
            .categoriaIngreso(EVENTOS).categoriaIngreso(CAFE_EN_GRANO);
    private final RepositoriosEnMemoria.UnidadDeTrabajoDirecta uow = new RepositoriosEnMemoria.UnidadDeTrabajoDirecta();
    private final ConsultarCatalogoDeVenta casoDeUso = new ConsultarCatalogoDeVenta(catalogo, uow);

    @Test
    void elAdministradorVeLosProductosConSusAdicionesYLasCategoriasDeIngreso() {
        CatalogoDeVentaResponse r = casoDeUso.consultar(ADMIN);

        assertEquals(List.of(
                new ProductoResponse(LATTE.getId(), "Café latte", new BigDecimal("6500"), List.of(
                        new AdicionResponse(ALMENDRA.getId(), "Leche de almendra", new BigDecimal("1500")),
                        new AdicionResponse(SHOT.getId(), "Shot extra de espresso", new BigDecimal("2000")))),
                new ProductoResponse(PANDEBONO.getId(), "Pandebono", new BigDecimal("2500"), List.of())),
                r.productos());
        assertEquals(List.of(new CategoriaResponse(EVENTOS.getId(), "Eventos privados"),
                new CategoriaResponse(CAFE_EN_GRANO.getId(), "Venta de café en grano")), r.categoriasIngreso());
        assertEquals(CAFETERIA, uow.ultimaCafeteria);
    }

    @Test
    void elCajeroVeLosProductosPeroNoLasCategoriasDeIngreso() {
        CatalogoDeVentaResponse r = casoDeUso.consultar(CAJERO);

        assertEquals(2, r.productos().size());
        assertTrue(r.categoriasIngreso().isEmpty());
        assertEquals(0, catalogo.consultasDeCategorias);   // ni siquiera se consultan
    }

    @Test
    void seConsultaConLaCafeteriaElegida() {
        casoDeUso.consultar(LAURA_EN_B);

        assertEquals(CAFETERIA_B, uow.ultimaCafeteria);
    }
}
