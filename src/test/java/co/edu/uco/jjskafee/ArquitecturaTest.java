package co.edu.uco.jjskafee;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.util.List;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Reglas del MONOLITO MODULAR (se ejecutan con ./mvnw verify y en GitHub Actions).
 *
 * Módulos: compartido · catalogo · ingresos · egresos · movimientos
 * Dentro de cada módulo: api (contrato público) · dominio · aplicacion (casos de uso y puertos) · infraestructura
 */
@AnalyzeClasses(packages = "co.edu.uco.jjskafee", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {

    private static final String BASE = "co.edu.uco.jjskafee.";
    private static final String COMPARTIDO = BASE + "compartido..";
    private static final List<String> MODULOS_DE_NEGOCIO = List.of("catalogo", "ingresos", "egresos", "movimientos");

    // ------------------------------------------------------------------
    // 1. Fronteras entre módulos
    // ------------------------------------------------------------------

    /** Un módulo solo puede usar de otro lo que está en su paquete "api". */
    @ArchTest
    static void modulosSoloSeHablanPorSuApi(JavaClasses clases) {
        for (String origen : MODULOS_DE_NEGOCIO) {
            for (String destino : MODULOS_DE_NEGOCIO) {
                if (origen.equals(destino)) {
                    continue;
                }
                DescribedPredicate<JavaClass> internoDelDestino = resideInAPackage(BASE + destino + "..")
                        .and(DescribedPredicate.not(resideInAPackage(BASE + destino + ".api..")));
                noClasses().that().resideInAPackage(BASE + origen + "..")
                        .should().dependOnClassesThat(internoDelDestino)
                        .because("el módulo " + origen + " solo puede usar " + destino + ".api")
                        .check(clases);
            }
        }
    }

    /** El núcleo compartido no conoce a ningún módulo de negocio. */
    @ArchTest
    static final ArchRule compartidoNoDependeDeModulos = noClasses()
            .that().resideInAPackage(COMPARTIDO)
            .should().dependOnClassesThat().resideInAnyPackage(
                    BASE + "catalogo..", BASE + "ingresos..", BASE + "egresos..", BASE + "movimientos..")
            .because("compartido es la base de todos; si dependiera de un módulo habría ciclos");

    /** La api es un contrato estable: no expone ni usa clases internas de su propio módulo. */
    @ArchTest
    static final ArchRule apiNoDependeDeLoInterno = noClasses()
            .that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAnyPackage("..dominio..", "..aplicacion..", "..infraestructura..")
            .allowEmptyShould(true)
            .because("la api solo debe tener interfaces y records propios");

    @ArchTest
    static final ArchRule sinCiclosEntreModulos = slices()
            .matching(BASE + "(*)..")
            .should().beFreeOfCycles();

    // ------------------------------------------------------------------
    // 2. Capas dentro de cada módulo
    // ------------------------------------------------------------------

    @ArchTest
    static final ArchRule dominioNoConoceAplicacionNiInfraestructura = noClasses()
            .that().resideInAPackage("..dominio..")
            .should().dependOnClassesThat().resideInAnyPackage("..aplicacion..", "..infraestructura..", "..api..");

    @ArchTest
    static final ArchRule aplicacionNoConoceInfraestructura = noClasses()
            .that().resideInAPackage("..aplicacion..")
            .should().dependOnClassesThat().resideInAPackage("..infraestructura..");

    @ArchTest
    static final ArchRule dominioYAplicacionSonJavaPuro = noClasses()
            .that().resideInAnyPackage("..dominio..", "..aplicacion..", "..api..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "java.sql..", "com.google..", "org.slf4j..")
            .because("las reglas del negocio no dependen de frameworks; los beans se crean en infraestructura.configuracion");

    /** Los controladores hablan con puertos de entrada, nunca con persistencia ni con los casos de uso concretos. */
    @ArchTest
    static final ArchRule controladoresSoloUsanPuertosDeEntrada = noClasses()
            .that().resideInAPackage("..infraestructura.web..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..infraestructura.persistencia..", "..infraestructura.salida..", "..aplicacion")
            .because("un controlador depende del contrato (puerto), no de la implementación");

    @ArchTest
    static final ArchRule puertosSonInterfaces = classes()
            .that().resideInAPackage("..puertos..")
            .should().beInterfaces()
            .because("un puerto es un contrato; lo implementa un caso de uso o un adaptador");

    @ArchTest
    static final ArchRule apiSonInterfacesORecords = classes()
            .that().resideInAPackage("..api..")
            .should().beInterfaces().orShould().beAssignableTo(Record.class)
            .because("lo que un módulo publica son contratos y datos inmutables");

    // ------------------------------------------------------------------
    // 3. Decisiones técnicas
    // ------------------------------------------------------------------

    @ArchTest
    static final ArchRule sinJpa = noClasses()
            .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..")
            .because("el proyecto usa JDBC (JdbcTemplate), no JPA");

    @ArchTest
    static final ArchRule soloLaPersistenciaUsaJdbc = noClasses()
            .that().resideOutsideOfPackages("..infraestructura.persistencia..", "..infraestructura.configuracion..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..")
            .because("solo los repositorios Pg* hablan con la base de datos");

    @ArchTest
    static final ArchRule firebaseSoloEnSeguridad = noClasses()
            .that().resideOutsideOfPackage(BASE + "compartido.infraestructura.seguridad..")
            .should().dependOnClassesThat().resideInAPackage("com.google.firebase..")
            .because("Firebase es un módulo externo; solo el adaptador de seguridad lo conoce (ADR-0001)");
}
