package co.edu.uco.jjskafee.ingresos.dominio;

import co.edu.uco.jjskafee.compartido.dominio.Producto;
import co.edu.uco.jjskafee.compartido.dominio.enums.TipoProducto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static co.edu.uco.jjskafee.DatosPrueba.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductoDelMenuTest {

    private static Producto producto(String precio, TipoProducto tipo, boolean disponible) {
        return new Producto(UUID.randomUUID(), "Prueba", new BigDecimal(precio), tipo, disponible);
    }

    @Test
    void unProductoBaseConSusAdicionesPermitidas() {
        ProductoDelMenu latte = new ProductoDelMenu(LATTE, List.of(ALMENDRA, SHOT));

        assertSame(LATTE, latte.base());
        assertEquals(List.of(ALMENDRA, SHOT), latte.adiciones());
        assertEquals(List.of(), new ProductoDelMenu(PANDEBONO, null).adiciones());
    }

    @Test
    void laListaDeAdicionesNoSePuedeModificarDesdeAfuera() {
        List<Producto> adiciones = new ArrayList<>(List.of(SHOT));
        ProductoDelMenu espresso = new ProductoDelMenu(ESPRESSO, adiciones);
        adiciones.add(ALMENDRA);

        assertEquals(List.of(SHOT), espresso.adiciones());
        assertThrows(UnsupportedOperationException.class, () -> espresso.adiciones().add(ALMENDRA));
    }

    @Test
    void soloEntraLoQueUnaVentaAceptaria() {
        // Una adición no puede venderse sola, ni algo agotado o sin precio
        assertThrows(IllegalArgumentException.class, () -> new ProductoDelMenu(SHOT, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ProductoDelMenu(producto("4000", TipoProducto.BASE, false), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ProductoDelMenu(producto("0", TipoProducto.BASE, true), List.of()));
        // Las adiciones deben ser adiciones disponibles con precio
        assertThrows(IllegalArgumentException.class, () -> new ProductoDelMenu(LATTE, List.of(PANDEBONO)));
        assertThrows(IllegalArgumentException.class,
                () -> new ProductoDelMenu(LATTE, List.of(producto("1500", TipoProducto.ADICION, false))));
        assertThrows(IllegalArgumentException.class,
                () -> new ProductoDelMenu(LATTE, List.of(producto("0", TipoProducto.ADICION, true))));
    }
}
