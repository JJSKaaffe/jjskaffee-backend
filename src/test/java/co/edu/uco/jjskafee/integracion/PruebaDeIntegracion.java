package co.edu.uco.jjskafee.integracion;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Prueba de integración contra el PostgreSQL real (perfil "local": lee .env o las variables
 * de entorno JJSKAFFEE_DB_*). Necesita la base creada con db/01 y db/02 y los seeds db/03 y db/04.
 * Firebase se reemplaza por un verificador de tokens de prueba (no hay red ni credenciales).
 * Se corren con ./mvnw verify.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = "jjskaffee.firebase.habilitado=false")
@ActiveProfiles("local")
@Import(ConfiguracionDePrueba.class)
public @interface PruebaDeIntegracion {
}
