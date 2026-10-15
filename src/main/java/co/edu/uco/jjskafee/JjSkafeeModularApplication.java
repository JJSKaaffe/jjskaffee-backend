package co.edu.uco.jjskafee;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.TimeZone;

/**
 * JJSKaffee · registro de ingresos y egresos · monolito modular.
 *
 * Módulos (cada uno es un paquete hermano de esta clase):
 *   compartido  → núcleo compartido: usuario autenticado, transacción + RLS, mensajes, errores HTTP
 *   catalogo    → productos, adiciones y categorías (solo lectura)
 *   ingresos    → ventas (RF24–RF27) y deshacer en 60 s (ADR-0008)
 *   egresos     → gastos (RF14, RF18)
 *   movimientos → consulta de ingresos y egresos con totales (ADR-0004)
 *
 * Un módulo solo puede usar de otro lo que está en su paquete "api" (ArchUnit lo verifica).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class JjSkafeeModularApplication {

    public static void main(String[] args) {
        // Todas las fechas del backend en UTC-0 (recomendación del profesor)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(JjSkafeeModularApplication.class, args);
    }
}
