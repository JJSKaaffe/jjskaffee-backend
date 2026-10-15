package co.edu.uco.jjskafee.compartido.infraestructura.configuracion;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Propiedades propias (prefijo "jjskaffee" en application.yml).
 * Nada de valores sensibles aquí: las credenciales llegan por variables de entorno
 * o, en la nube, por Google Cloud Secret Manager (ADR-0014).
 */
@ConfigurationProperties(prefix = "jjskaffee")
public record PropiedadesJjskaffee(Seguridad seguridad, Cors cors) {

    public PropiedadesJjskaffee {
        seguridad = seguridad == null ? new Seguridad(null, null, null) : seguridad;
        cors = cors == null ? new Cors(null) : cors;
    }

    /**
     * @param modo             "firebase" (por defecto) o "desarrollo" (solo con el perfil local)
     * @param proyectoFirebase id del proyecto de Firebase
     * @param claimCafeteria   nombre del custom claim con el id de la cafetería (SPIKE-01)
     */
    public record Seguridad(String modo, String proyectoFirebase, String claimCafeteria) {
        public Seguridad {
            modo = modo == null ? "firebase" : modo;
            claimCafeteria = claimCafeteria == null ? "id_cafeteria" : claimCafeteria;
        }
    }

    /** @param origenes orígenes del frontend Vue que pueden llamar al API */
    public record Cors(List<String> origenes) {
        public Cors {
            origenes = origenes == null ? List.of() : List.copyOf(origenes);
        }
    }
}
