package co.edu.uco.jjskafee;

import co.edu.uco.jjskafee.compartido.dominio.mensajes.CodigoMensaje;
import co.edu.uco.jjskafee.compartido.dominio.mensajes.MensajesCompartidos;
import co.edu.uco.jjskafee.egresos.dominio.MensajesEgresos;
import co.edu.uco.jjskafee.egresos.infraestructura.web.EgresoRequest;
import co.edu.uco.jjskafee.ingresos.dominio.MensajesIngresos;
import co.edu.uco.jjskafee.ingresos.infraestructura.web.IngresoRequest;
import co.edu.uco.jjskafee.movimientos.dominio.MensajesMovimientos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "No quemar los mensajes": garantiza que todo texto para el usuario está en i18n/mensajes.properties.
 * - Cada clave de los enums Mensajes* existe y no está vacía.
 * - Cada {clave} usada en @NotNull, @Size... de los request existe.
 * - No hay claves huérfanas en el archivo (todas se usan en algún lado).
 */
class MensajesTest {

    private static final String ARCHIVO = "/i18n/mensajes.properties";
    private static final Pattern CLAVE_EN_ANOTACION = Pattern.compile("^\\{([a-z0-9.\\-]+)}$");

    private static final List<Class<? extends Enum<? extends CodigoMensaje>>> ENUMS = List.of(
            MensajesCompartidos.class, MensajesIngresos.class, MensajesEgresos.class, MensajesMovimientos.class);

    private static final List<Class<?>> REQUESTS = List.of(
            IngresoRequest.class, IngresoRequest.ItemRequest.class, IngresoRequest.AdicionRequest.class,
            EgresoRequest.class);

    private static Properties mensajes;

    @BeforeAll
    static void cargar() throws IOException {
        mensajes = new Properties();
        try (InputStream entrada = MensajesTest.class.getResourceAsStream(ARCHIVO)) {
            assertNotNull(entrada, "No se encontró " + ARCHIVO);
            mensajes.load(new InputStreamReader(entrada, StandardCharsets.UTF_8));
        }
    }

    @Test
    void todasLasClavesDeLosEnumsTienenTexto() {
        for (String clave : clavesDeEnums()) {
            String texto = mensajes.getProperty(clave);
            assertNotNull(texto, "Falta la clave en mensajes.properties: " + clave);
            assertFalse(texto.isBlank(), "La clave está vacía: " + clave);
        }
    }

    @Test
    void todasLasClavesDeValidacionTienenTexto() {
        for (String clave : clavesDeValidacion()) {
            assertNotNull(mensajes.getProperty(clave), "Falta la clave de validación: " + clave);
        }
    }

    @Test
    void noHayClavesHuerfanas() {
        Set<String> usadas = new HashSet<>(clavesDeEnums());
        usadas.addAll(clavesDeValidacion());
        for (String clave : mensajes.stringPropertyNames()) {
            assertTrue(usadas.contains(clave), "Clave sin uso en el código (bórrala o úsala): " + clave);
        }
    }

    @Test
    void lasClavesDeLosEnumsNoSeRepiten() {
        List<String> claves = clavesDeEnums();
        assertTrue(claves.size() == new HashSet<>(claves).size(), "Hay claves repetidas entre los enums");
    }

    private static List<String> clavesDeEnums() {
        List<String> claves = new ArrayList<>();
        for (Class<? extends Enum<? extends CodigoMensaje>> tipo : ENUMS) {
            for (Enum<? extends CodigoMensaje> valor : tipo.getEnumConstants()) {
                claves.add(((CodigoMensaje) valor).clave());
            }
        }
        return claves;
    }

    /** Lee el atributo message() de las anotaciones de los campos de los request (y de List<@Valid ...>). */
    private static Set<String> clavesDeValidacion() {
        Set<String> claves = new HashSet<>();
        for (Class<?> request : REQUESTS) {
            for (RecordComponent componente : request.getRecordComponents()) {
                // Según su @Target, la anotación queda en el accesor (METHOD) o en el tipo (TYPE_USE)
                Stream.of(componente.getAnnotations(), componente.getAccessor().getAnnotations(),
                                componente.getAnnotatedType().getAnnotations())
                        .flatMap(Stream::of)
                        .forEach(anotacion -> claveDe(anotacion, claves));
            }
        }
        return claves;
    }

    private static void claveDe(Annotation anotacion, Set<String> claves) {
        try {
            Method message = anotacion.annotationType().getMethod("message");
            Matcher m = CLAVE_EN_ANOTACION.matcher((String) message.invoke(anotacion));
            if (m.matches()) {
                claves.add(m.group(1));
            }
        } catch (NoSuchMethodException e) {
            // @Valid y otras anotaciones sin mensaje
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
