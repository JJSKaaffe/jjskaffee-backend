package co.edu.uco.jjskafee;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El openapi.yaml del API Gateway (ADR-0017) y los controllers del backend dicen lo mismo: cada endpoint
 * está en el gateway bajo /api con su método y su OPTIONS, toda operación salvo OPTIONS exige el token de
 * Firebase y el gateway no tiene rutas que el backend no atienda. Si falla, se corrige el yaml o el
 * controller, no la prueba.
 */
class ContratoApiGatewayTest {

    private static final Path OPENAPI = Path.of("infra", "api-gateway", "openapi.yaml");
    private static final String PREFIJO = "/api";
    private static final String OPTIONS = "options";
    private static final String SEGURIDAD = "firebase";

    /** Endpoints del backend como "post /api/ingresos". */
    private static Set<String> endpointsDelBackend;
    private static Set<String> controllers;
    private static Map<String, Object> openapi;
    private static Map<String, Map<String, Object>> rutas;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void cargar() throws IOException {
        endpointsDelBackend = new TreeSet<>();
        controllers = new TreeSet<>();
        for (JavaClass clase : new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("co.edu.uco.jjskafee")) {
            if (clase.isAnnotatedWith(RestController.class)) {
                controllers.add(clase.getSimpleName());
                agregarEndpoints(clase.reflect());
            }
        }

        try (Reader lector = Files.newBufferedReader(OPENAPI)) {
            openapi = new Yaml(new SafeConstructor(new LoaderOptions())).load(lector);
        }
        rutas = (Map<String, Map<String, Object>>) openapi.get("paths");
    }

    private static void agregarEndpoints(Class<?> controller) {
        RequestMapping deLaClase = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        String base = deLaClase == null || deLaClase.path().length == 0 ? "" : deLaClase.path()[0];
        for (Method metodo : controller.getDeclaredMethods()) {
            RequestMapping mapeo = AnnotatedElementUtils.findMergedAnnotation(metodo, RequestMapping.class);
            if (mapeo == null) {
                continue;
            }
            assertTrue(mapeo.method().length > 0,
                    controller.getSimpleName() + "." + metodo.getName() + " debe declarar su método HTTP");
            String[] caminos = mapeo.path().length == 0 ? new String[]{""} : mapeo.path();
            for (String camino : caminos) {
                for (var metodoHttp : mapeo.method()) {
                    endpointsDelBackend.add(metodoHttp.name().toLowerCase(Locale.ROOT) + " " + PREFIJO + base + camino);
                }
            }
        }
    }

    @Test
    void seLeyeronLosCuatroControllers() {
        assertTrue(controllers.containsAll(Set.of(
                "AccesoController", "IngresosController", "EgresosController", "MovimientosController")), controllers.toString());
        assertFalse(endpointsDelBackend.isEmpty());
        assertEquals("2.0", openapi.get("swagger"), "API Gateway solo acepta OpenAPI 2.0");
    }

    @Test
    void cadaEndpointDelBackendEstaEnElGatewayConSuMetodoYSuOptions() {
        List<String> faltan = new ArrayList<>();
        for (String endpoint : endpointsDelBackend) {
            String metodo = endpoint.substring(0, endpoint.indexOf(' '));
            String ruta = endpoint.substring(endpoint.indexOf(' ') + 1);
            Map<String, Object> operaciones = rutas.get(ruta);
            if (operaciones == null || !operaciones.containsKey(metodo)) {
                faltan.add(endpoint);
            }
            if (operaciones == null || !operaciones.containsKey(OPTIONS)) {
                faltan.add(OPTIONS + " " + ruta);
            }
        }
        assertEquals(List.of(), faltan, "Faltan en infra/api-gateway/openapi.yaml");
    }

    @Test
    void elGatewayNoTieneRutasQueElBackendNoAtienda() {
        List<String> sobran = new ArrayList<>();
        rutas.forEach((ruta, operaciones) -> {
            if (!ruta.startsWith(PREFIJO + "/")) {
                sobran.add(ruta + " (sin el prefijo /api)");
            }
            for (String metodo : operaciones.keySet()) {
                if (!OPTIONS.equals(metodo) && !endpointsDelBackend.contains(metodo + " " + ruta)) {
                    sobran.add(metodo + " " + ruta);
                }
            }
            boolean tieneOtroMetodo = operaciones.keySet().stream().anyMatch(m -> !OPTIONS.equals(m));
            if (!tieneOtroMetodo) {
                sobran.add(ruta + " (solo OPTIONS)");
            }
        });
        assertEquals(List.of(), sobran, "Sobran en infra/api-gateway/openapi.yaml");
    }

    @Test
    @SuppressWarnings("unchecked")
    void todaOperacionSalvoOptionsExigeElTokenDeFirebase() {
        List<Object> global = (List<Object>) openapi.get("security");
        assertTrue(exigeFirebase(global), "La seguridad global debe ser firebase");
        Map<String, Object> definiciones = (Map<String, Object>) openapi.get("securityDefinitions");
        assertNotNull(definiciones);
        assertTrue(definiciones.containsKey(SEGURIDAD), "Falta securityDefinitions.firebase");

        List<String> malas = new ArrayList<>();
        rutas.forEach((ruta, operaciones) -> operaciones.forEach((metodo, valor) -> {
            Map<String, Object> operacion = (Map<String, Object>) valor;
            List<Object> propia = (List<Object>) operacion.get("security");
            if (OPTIONS.equals(metodo)) {
                // El preflight de CORS no lleva token: debe decirlo explícitamente
                if (propia == null || !propia.isEmpty()) {
                    malas.add(metodo + " " + ruta + " debe tener security: []");
                }
            } else if (!exigeFirebase(propia == null ? global : propia)) {
                malas.add(metodo + " " + ruta + " no exige firebase");
            }
        }));
        assertEquals(List.of(), malas);
    }

    /** Cada alternativa de seguridad debe incluir firebase (en OpenAPI la lista es un "o"). */
    private static boolean exigeFirebase(List<Object> seguridad) {
        return seguridad != null && !seguridad.isEmpty()
                && seguridad.stream().allMatch(r -> r instanceof Map<?, ?> m && m.containsKey(SEGURIDAD));
    }
}
