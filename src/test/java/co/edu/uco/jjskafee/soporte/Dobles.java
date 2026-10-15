package co.edu.uco.jjskafee.soporte;

import co.edu.uco.jjskafee.compartido.aplicacion.puertos.ProveedorMensajes;
import co.edu.uco.jjskafee.compartido.aplicacion.puertos.UnidadDeTrabajo;
import co.edu.uco.jjskafee.compartido.dominio.Rol;
import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Dobles de prueba compartidos y datos fijos (los mismos UUID de db/jjskaffee_completo.sql). */
public final class Dobles {

    public static final UUID CAFE_DEMO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID CAFE_VECINO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    public static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(
            UUID.fromString("a0000000-0000-0000-0000-000000000001"), CAFE_DEMO, "Laura Gómez", Rol.ADMINISTRADOR);
    public static final UsuarioAutenticado CAJERO = new UsuarioAutenticado(
            UUID.fromString("a0000000-0000-0000-0000-000000000002"), CAFE_DEMO, "Andrés Ruiz", Rol.CAJERO);
    public static final UsuarioAutenticado ADMIN_VECINO = new UsuarioAutenticado(
            UUID.fromString("a0000000-0000-0000-0000-000000000003"), CAFE_VECINO, "Marta López", Rol.ADMINISTRADOR);

    public static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");

    private Dobles() {
    }

    public static Clock relojFijo(Instant instante) {
        return Clock.fixed(instante, ZoneOffset.UTC);
    }

    /** Ejecuta la operación sin BD y recuerda con qué cafetería se llamó. */
    public static final class UnidadDeTrabajoEnMemoria implements UnidadDeTrabajo {
        public final List<UUID> cafeterias = new ArrayList<>();

        @Override
        public <T> T ejecutar(UUID idCafeteria, Supplier<T> operacion) {
            cafeterias.add(idCafeteria);
            return operacion.get();
        }
    }

    /** Devuelve la clave del mensaje (las pruebas no dependen del texto). */
    public static final class MensajesPorClave implements ProveedorMensajes {
        @Override
        public String obtener(co.edu.uco.jjskafee.compartido.dominio.mensajes.CodigoMensaje codigo, Object... argumentos) {
            return codigo.clave();
        }
    }
}
