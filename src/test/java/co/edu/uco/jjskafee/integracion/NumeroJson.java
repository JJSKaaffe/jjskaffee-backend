package co.edu.uco.jjskafee.integracion;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import java.math.BigDecimal;

/**
 * Compara números de una respuesta JSON por su valor: 8500, 8500.0 y 8500.00 son iguales.
 * Hace falta con jsonPath de filtros ([?(...)]), que devuelven listas sin convertir el tipo del número.
 */
final class NumeroJson {

    private NumeroJson() { }

    static Matcher<Object> numero(String esperado) {
        BigDecimal valor = new BigDecimal(esperado);
        return new BaseMatcher<>() {
            @Override
            public boolean matches(Object actual) {
                return actual instanceof Number n && new BigDecimal(n.toString()).compareTo(valor) == 0;
            }

            @Override
            public void describeTo(Description descripcion) {
                descripcion.appendText("el número ").appendValue(valor);
            }
        };
    }
}
