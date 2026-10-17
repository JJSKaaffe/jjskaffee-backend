package co.edu.uco.jjskafee;

import co.edu.uco.jjskafee.dominio.Categoria;
import co.edu.uco.jjskafee.dominio.Producto;
import co.edu.uco.jjskafee.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.dominio.enums.Rol;
import co.edu.uco.jjskafee.dominio.enums.TipoCategoria;
import co.edu.uco.jjskafee.dominio.enums.TipoProducto;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

/** Datos de prueba iguales a los seeds db/03_seed_cafeterias_y_catalogo.sql y db/04_seed_usuarios.sql (mismos UUID). */
public final class DatosPrueba {

    private DatosPrueba() { }

    public static final UUID CAFETERIA = UUID.fromString("11111111-1111-1111-1111-111111111111");

    public static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(
            UUID.fromString("a0000000-0000-0000-0000-000000000001"), CAFETERIA, "Laura Gómez", Rol.ADMINISTRADOR);
    public static final UsuarioAutenticado CAJERO = new UsuarioAutenticado(
            UUID.fromString("a0000000-0000-0000-0000-000000000002"), CAFETERIA, "Andrés Ruiz", Rol.CAJERO);

    public static final Categoria INSUMOS = new Categoria(
            UUID.fromString("c0000000-0000-0000-0000-000000000011"), "Insumos", TipoCategoria.EGRESO, false);
    public static final Categoria SERVICIOS = new Categoria(
            UUID.fromString("c0000000-0000-0000-0000-000000000012"), "Servicios públicos", TipoCategoria.EGRESO, false);
    public static final Categoria ARCHIVADA = new Categoria(
            UUID.fromString("c0000000-0000-0000-0000-000000000015"), "Mantenimiento (antigua)", TipoCategoria.EGRESO, true);
    public static final Categoria BEBIDAS = new Categoria(
            UUID.fromString("c0000000-0000-0000-0000-000000000001"), "Bebidas calientes", TipoCategoria.MENU, false);

    public static final Producto LATTE = producto("02", "Café latte", 6500, TipoProducto.BASE);
    public static final Producto PANDEBONO = producto("05", "Pandebono", 2500, TipoProducto.BASE);
    public static final Producto ALMENDRA = producto("11", "Leche de almendra", 1500, TipoProducto.ADICION);
    public static final Producto SHOT = producto("12", "Shot extra de espresso", 2000, TipoProducto.ADICION);

    /** Adiciones que se le pueden poner al latte (tabla producto_adicion). */
    public static final Set<UUID> ADICIONES_LATTE = Set.of(ALMENDRA.getId(), SHOT.getId());

    /** uid de Firebase de los usuarios de prueba (columna usuarios.firebase_uid). */
    public static final String UID_ADMIN = "uid-prueba-laura";
    public static final String UID_CAJERO = "uid-prueba-andres";

    // Segunda cafetería: sirve para probar que A y B no ven los datos de la otra (RLS)
    public static final UUID CAFETERIA_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UsuarioAutenticado ADMIN_B = new UsuarioAutenticado(
            UUID.fromString("a0000000-0000-0000-0000-000000000003"), CAFETERIA_B, "Marta Díaz", Rol.ADMINISTRADOR);
    public static final UsuarioAutenticado CAJERO_B = new UsuarioAutenticado(
            UUID.fromString("a0000000-0000-0000-0000-000000000004"), CAFETERIA_B, "Camilo Peña", Rol.CAJERO);
    public static final Categoria INSUMOS_B = new Categoria(
            UUID.fromString("c0000000-0000-0000-0000-000000000031"), "Insumos", TipoCategoria.EGRESO, false);
    public static final Producto CAPUCHINO_B = producto("21", "Capuchino", 7000, TipoProducto.BASE);

    public static final Instant AHORA = Instant.parse("2026-10-11T15:00:00Z");
    public static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);

    private static Producto producto(String n, String nombre, int precio, TipoProducto tipo) {
        return new Producto(UUID.fromString("b0000000-0000-0000-0000-0000000000" + n), nombre,
                BigDecimal.valueOf(precio), tipo, true);
    }
}
