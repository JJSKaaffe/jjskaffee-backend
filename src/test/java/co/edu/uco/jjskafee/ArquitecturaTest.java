package co.edu.uco.jjskafee;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Monolito modular con módulos hexagonales (ADR-0015). Las reglas hexagonales se aplican dentro de
 * cada módulo (el "*" del paquete es el módulo) y las reglas entre módulos protegen sus fronteras.
 */
@AnalyzeClasses(packages = "co.edu.uco.jjskafee", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {

    private static final String RAIZ = "co.edu.uco.jjskafee";
    private static final String COMPARTIDO = "compartido";

    private static final String DOMINIO = RAIZ + ".*.dominio..";
    private static final String SERVICIOS = RAIZ + ".*.servicios..";
    private static final String INFRAESTRUCTURA = RAIZ + ".*.infraestructura..";
    private static final String TRANSVERSAL = RAIZ + ".*.transversal..";

    // ----- Reglas hexagonales, dentro de cada módulo -----

    @ArchTest
    static final ArchRule capasRespetanArquitecturaHexagonal = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .withOptionalLayers(true)
            .layer("Dominio").definedBy(DOMINIO)
            .layer("Servicios").definedBy(SERVICIOS)
            .layer("Infraestructura").definedBy(INFRAESTRUCTURA)
            .layer("Transversal").definedBy(TRANSVERSAL)
            .whereLayer("Infraestructura").mayNotBeAccessedByAnyLayer()
            .whereLayer("Servicios").mayOnlyBeAccessedByLayers("Infraestructura")
            .whereLayer("Dominio").mayOnlyBeAccessedByLayers("Servicios", "Infraestructura")
            .whereLayer("Transversal").mayOnlyBeAccessedByLayers("Dominio", "Servicios", "Infraestructura");

    @ArchTest
    static final ArchRule dominioNoDependeDeFrameworks = noClasses()
            .that().resideInAPackage(DOMINIO)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "jakarta.servlet..")
            .allowEmptyShould(true)
            .because("el dominio debe ser independiente de frameworks e infraestructura");

    @ArchTest
    static final ArchRule serviciosNoDependenDeSpringWeb = noClasses()
            .that().resideInAPackage(SERVICIOS)
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..")
            .allowEmptyShould(true)
            .because("los casos de uso no deben conocer detalles HTTP");

    @ArchTest
    static final ArchRule controladoresSoloUsanPuertosDeEntrada = noClasses()
            .that().resideInAPackage(RAIZ + ".*.infraestructura.input..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    RAIZ + ".*.infraestructura.output..",
                    RAIZ + ".*.servicios.casouso..")
            .allowEmptyShould(true)
            .because("los adaptadores de entrada deben hablar con los puertos de entrada, no con los casos de uso ni con la salida");

    @ArchTest
    static final ArchRule puertosSonInterfaces = classes()
            .that().resideInAnyPackage(RAIZ + ".*.servicios.input.ports..",
                                       RAIZ + ".*.servicios.output.ports..")
            .should().beInterfaces()
            .allowEmptyShould(true)
            .because("un puerto es un contrato; lo implementa un caso de uso o un adaptador");

    @ArchTest
    static final ArchRule serviciosSinSpringNiBaseDeDatos = noClasses()
            .that().resideInAPackage(SERVICIOS)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "java.sql..", "com.google.firebase..")
            .allowEmptyShould(true)
            .because("los casos de uso son Java puro; se crean en la configuración, no con anotaciones");

    @ArchTest
    static final ArchRule sinJpa = noClasses()
            .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..")
            .allowEmptyShould(true)
            .because("el proyecto usa JDBC (JdbcTemplate), no JPA");

    // ----- Reglas entre módulos -----

    @ArchTest
    static final ArchRule modulosSoloSeUsanPorSusPuertosDeEntrada = classes()
            .that().resideInAPackage(RAIZ + "..")
            .should(usarDeOtrosModulosSoloSusPuertosDeEntrada())
            .because("un módulo solo expone sus puertos de entrada y DTO (<modulo>.servicios.input..); "
                    + "su dominio, casos de uso e infraestructura son internos");

    @ArchTest
    static final ArchRule compartidoNoDependeDeLosModulos = noClasses()
            .that().resideInAPackage(RAIZ + "." + COMPARTIDO + "..")
            .should().dependOnClassesThat(resideInAPackage(RAIZ + "..")
                    .and(resideInAPackage(RAIZ + "." + COMPARTIDO + "..").negate()))
            .because("el kernel compartido es la base de todos los módulos y no puede depender de ninguno");

    @ArchTest
    static final ArchRule sinCiclosEntreModulos = slices()
            .matching(RAIZ + ".(*)..")
            .should().beFreeOfCycles()
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule sinCiclosEntrePaquetes = slices()
            .matching(RAIZ + ".(**)")
            .should().beFreeOfCycles()
            .allowEmptyShould(true);

    /** Módulo de una clase: el primer paquete debajo de la raíz (null si está en la raíz o fuera de ella). */
    private static String moduloDe(JavaClass clase) {
        String paquete = clase.getPackageName();
        if (!paquete.startsWith(RAIZ + ".")) {
            return null;
        }
        String resto = paquete.substring(RAIZ.length() + 1);
        int punto = resto.indexOf('.');
        return punto < 0 ? resto : resto.substring(0, punto);
    }

    private static ArchCondition<JavaClass> usarDeOtrosModulosSoloSusPuertosDeEntrada() {
        return new ArchCondition<>("usar de otros módulos solo lo que está en <modulo>.servicios.input..") {
            @Override
            public void check(JavaClass clase, ConditionEvents eventos) {
                String origen = moduloDe(clase);
                if (origen == null) {
                    return;
                }
                for (Dependency dependencia : clase.getDirectDependenciesFromSelf()) {
                    JavaClass destino = dependencia.getTargetClass().getBaseComponentType();
                    String moduloDestino = moduloDe(destino);
                    if (moduloDestino == null || moduloDestino.equals(origen) || moduloDestino.equals(COMPARTIDO)) {
                        continue;
                    }
                    String expuesto = RAIZ + "." + moduloDestino + ".servicios.input";
                    String paqueteDestino = destino.getPackageName();
                    if (!paqueteDestino.equals(expuesto) && !paqueteDestino.startsWith(expuesto + ".")) {
                        eventos.add(SimpleConditionEvent.violated(dependencia, dependencia.getDescription()));
                    }
                }
            }
        };
    }
}
