package co.edu.uco.jjskafee;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "co.edu.uco.jjskafee", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {

    private static final String DOMINIO = "co.edu.uco.jjskafee.dominio..";
    private static final String SERVICIOS = "co.edu.uco.jjskafee.servicios..";
    private static final String INFRAESTRUCTURA = "co.edu.uco.jjskafee.infraestructura..";
    private static final String TRANSVERSAL = "co.edu.uco.jjskafee.transversal..";

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
            .that().resideInAPackage("co.edu.uco.jjskafee.infraestructura.input..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "co.edu.uco.jjskafee.infraestructura.output..",
                    "co.edu.uco.jjskafee.servicios.casouso..")
            .allowEmptyShould(true)
            .because("los adaptadores de entrada deben hablar con los puertos de entrada, no con los casos de uso ni con la salida");

    @ArchTest
    static final ArchRule sinCiclosEntrePaquetes = slices()
            .matching("co.edu.uco.jjskafee.(*)..")
            .should().beFreeOfCycles()
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule puertosSonInterfaces = classes()
            .that().resideInAnyPackage("co.edu.uco.jjskafee.servicios.input.ports..",
                                       "co.edu.uco.jjskafee.servicios.output.ports..")
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
}
