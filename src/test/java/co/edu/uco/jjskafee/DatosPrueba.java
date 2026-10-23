package co.edu.uco.jjskafee;

import co.edu.uco.jjskafee.compartido.dominio.Categoria;
import co.edu.uco.jjskafee.compartido.dominio.Producto;
import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.compartido.dominio.enums.Rol;
import co.edu.uco.jjskafee.compartido.dominio.enums.TipoCategoria;
import co.edu.uco.jjskafee.compartido.dominio.enums.TipoProducto;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

/** Datos de prueba iguales a los seeds de db/ (03, 04, 06, 08 y 10; mismos UUID). */
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

    // Categorías de ingreso de la cafetería A (db/10_seed_categorias_ingreso.sql), para ingresos libres
    public static final Categoria EVENTOS = new Categoria(
            UUID.fromString("c0000000-0000-0000-0000-000000000051"), "Eventos privados", TipoCategoria.INGRESO, false);
    public static final Categoria CAFE_EN_GRANO = new Categoria(
            UUID.fromString("c0000000-0000-0000-0000-000000000052"), "Venta de café en grano", TipoCategoria.INGRESO, false);
    public static final Categoria CONVENIOS_ARCHIVADA = new Categoria(
            UUID.fromString("c0000000-0000-0000-0000-000000000055"), "Convenios (antigua)", TipoCategoria.INGRESO, true);

    public static final Producto ESPRESSO = producto("01", "Espresso", 4000, TipoProducto.BASE);
    public static final Producto LATTE = producto("02", "Café latte", 6500, TipoProducto.BASE);
    public static final Producto PANDEBONO = producto("05", "Pandebono", 2500, TipoProducto.BASE);
    public static final Producto ALMENDRA = producto("11", "Leche de almendra", 1500, TipoProducto.ADICION);
    public static final Producto SHOT = producto("12", "Shot extra de espresso", 2000, TipoProducto.ADICION);

    /** Adiciones que se le pueden poner al latte (tabla producto_adicion). */
    public static final Set<UUID> ADICIONES_LATTE = Set.of(ALMENDRA.getId(), SHOT.getId());

    /** uid de Firebase de los usuarios de prueba (columna usuarios.firebase_uid). */
    public static final String UID_ADMIN = "uid-prueba-laura";
    public static final String UID_CAJERO = "uid-prueba-andres";
    public static final String UID_ADMIN_B = "uid-prueba-marta";
    public static final String UID_CAJERO_B = "uid-prueba-camilo";
    public static final String UID_CAJERO_INACTIVA = "uid-prueba-sofia";
    /** Persona que inició sesión en Firebase pero no pertenece a ninguna cafetería. */
    public static final String UID_SIN_MEMBRESIA = "uid-prueba-sin-cafeteria";

    public static final String NOMBRE_CAFETERIA = "Café La Esquina (A)";
    public static final String NOMBRE_CAFETERIA_B = "Café El Parque (B)";

    // Segunda cafetería: sirve para probar que A y B no ven los datos de la otra (RLS)
    public static final UUID CAFETERIA_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UsuarioAutenticado ADMIN_B = new UsuarioAutenticado(
            UUID.fromString("a0000000-0000-0000-0000-000000000003"), CAFETERIA_B, "Marta Díaz", Rol.ADMINISTRADOR);
    public static final UsuarioAutenticado CAJERO_B = new UsuarioAutenticado(
            UUID.fromString("a0000000-0000-0000-0000-000000000004"), CAFETERIA_B, "Camilo Peña", Rol.CAJERO);
    public static final Categoria INSUMOS_B = new Categoria(
            UUID.fromString("c0000000-0000-0000-0000-000000000031"), "Insumos", TipoCategoria.EGRESO, false);
    public static final Producto CAPUCHINO_B = producto("21", "Capuchino", 7000, TipoProducto.BASE);

    // Multi-cafetería (db/08_seed_multicafeteria.sql): Laura es ADMINISTRADORA en A (ADMIN) y CAJERA en B
    public static final UsuarioAutenticado LAURA_EN_B = new UsuarioAutenticado(
            UUID.fromString("a0000000-0000-0000-0000-000000000006"), CAFETERIA_B, "Laura Gómez", Rol.CAJERO);

    // Tercera cafetería, inactiva (db/06_seed_cafeteria_inactiva.sql): no puede registrar ingresos
    public static final UUID CAFETERIA_INACTIVA = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final UsuarioAutenticado CAJERO_INACTIVA = new UsuarioAutenticado(
            UUID.fromString("a0000000-0000-0000-0000-000000000005"), CAFETERIA_INACTIVA, "Sofía Mora", Rol.CAJERO);
    public static final Producto TINTO_INACTIVA = producto("31", "Tinto", 2000, TipoProducto.BASE);

    public static final Instant AHORA = Instant.parse("2026-10-11T15:00:00Z");
    public static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);

    private static Producto producto(String n, String nombre, int precio, TipoProducto tipo) {
        return new Producto(UUID.fromString("b0000000-0000-0000-0000-0000000000" + n), nombre,
                BigDecimal.valueOf(precio), tipo, true);
    }
}
