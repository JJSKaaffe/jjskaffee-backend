package co.edu.uco.jjskafee;

import co.edu.uco.jjskafee.acceso.dominio.CafeteriaDelUsuario;
import co.edu.uco.jjskafee.acceso.servicios.output.ports.MembresiaRepository;
import co.edu.uco.jjskafee.compartido.dominio.Categoria;
import co.edu.uco.jjskafee.compartido.dominio.Producto;
import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.compartido.servicios.output.ports.CatalogoRepository;
import co.edu.uco.jjskafee.compartido.servicios.output.ports.UnidadDeTrabajo;
import co.edu.uco.jjskafee.egresos.dominio.Egreso;
import co.edu.uco.jjskafee.egresos.servicios.output.ports.EgresoRepository;
import co.edu.uco.jjskafee.ingresos.dominio.IngresoLibre;
import co.edu.uco.jjskafee.ingresos.dominio.ProductoDelMenu;
import co.edu.uco.jjskafee.ingresos.dominio.Venta;
import co.edu.uco.jjskafee.ingresos.servicios.output.ports.CafeteriaRepository;
import co.edu.uco.jjskafee.ingresos.servicios.output.ports.CatalogoDeVentaRepository;
import co.edu.uco.jjskafee.ingresos.servicios.output.ports.IngresoLibreRepository;
import co.edu.uco.jjskafee.ingresos.servicios.output.ports.VentaRepository;
import co.edu.uco.jjskafee.movimientos.dominio.FiltroMovimientos;
import co.edu.uco.jjskafee.movimientos.dominio.Movimiento;
import co.edu.uco.jjskafee.movimientos.dominio.ResultadoMovimientos;
import co.edu.uco.jjskafee.movimientos.servicios.output.ports.MovimientoRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Implementaciones falsas de los puertos de salida, en memoria.
 * Gracias a la hexagonal, los casos de uso se prueban sin base de datos ni Spring.
 * Es pública porque la usan las pruebas de los casos de uso de todos los módulos.
 */
public final class RepositoriosEnMemoria {

    private RepositoriosEnMemoria() { }

    public static class Catalogo implements CatalogoRepository {
        public final Map<UUID, Producto> productos = new HashMap<>();
        public final Map<UUID, Set<UUID>> adiciones = new HashMap<>();
        public final Map<UUID, Categoria> categorias = new HashMap<>();

        public Catalogo producto(Producto p) { productos.put(p.getId(), p); return this; }
        public Catalogo adiciones(Producto base, Set<UUID> ids) { adiciones.put(base.getId(), ids); return this; }
        public Catalogo categoria(Categoria c) { categorias.put(c.getId(), c); return this; }

        @Override public Optional<Producto> buscarProducto(UUID id) { return Optional.ofNullable(productos.get(id)); }
        @Override public Set<UUID> adicionesPermitidas(UUID idBase) { return adiciones.getOrDefault(idBase, Set.of()); }
        @Override public Optional<Categoria> buscarCategoria(UUID id) { return Optional.ofNullable(categorias.get(id)); }
    }

    public static class Egresos implements EgresoRepository {
        public final List<Egreso> guardados = new ArrayList<>();
        @Override public void guardar(Egreso egreso) { guardados.add(egreso); }
    }

    /** Como la UNIQUE (id_cafeteria, clave_idempotencia) de la BD: no guarda una segunda venta con la misma clave. */
    public static class Ventas implements VentaRepository {
        public final Map<UUID, Venta> guardadas = new HashMap<>();
        @Override
        public boolean guardar(Venta venta) {
            if (venta.getClaveIdempotencia() != null && guardadas.values().stream().anyMatch(v ->
                    v.getIdCafeteria().equals(venta.getIdCafeteria())
                            && venta.getClaveIdempotencia().equals(v.getClaveIdempotencia()))) {
                return false;
            }
            guardadas.put(venta.getId(), venta);
            return true;
        }
        @Override public Optional<Venta> buscarPorId(UUID id) { return Optional.ofNullable(guardadas.get(id)); }
        @Override
        public Optional<Venta> buscarPorClaveIdempotencia(UUID clave) {
            return guardadas.values().stream().filter(v -> clave.equals(v.getClaveIdempotencia())).findFirst();
        }
        @Override public void actualizarEstado(Venta venta) { guardadas.put(venta.getId(), venta); }
    }

    /** Como la UNIQUE (id_cafeteria, clave_idempotencia) de ingreso_libre: no guarda un segundo ingreso con la misma clave. */
    public static class IngresosLibres implements IngresoLibreRepository {
        public final Map<UUID, IngresoLibre> guardados = new HashMap<>();
        @Override
        public boolean guardar(IngresoLibre ingreso) {
            if (ingreso.getClaveIdempotencia() != null && guardados.values().stream().anyMatch(i ->
                    i.getIdCafeteria().equals(ingreso.getIdCafeteria())
                            && ingreso.getClaveIdempotencia().equals(i.getClaveIdempotencia()))) {
                return false;
            }
            guardados.put(ingreso.getId(), ingreso);
            return true;
        }
        @Override public Optional<IngresoLibre> buscarPorId(UUID id) { return Optional.ofNullable(guardados.get(id)); }
        @Override
        public Optional<IngresoLibre> buscarPorClaveIdempotencia(UUID clave) {
            return guardados.values().stream().filter(i -> clave.equals(i.getClaveIdempotencia())).findFirst();
        }
    }

    /** Catálogo de la pantalla de venta: devuelve lo que se le configure y cuenta cuántas veces se consultó. */
    public static class CatalogoDeVenta implements CatalogoDeVentaRepository {
        public final List<ProductoDelMenu> productos = new ArrayList<>();
        public final List<Categoria> categoriasIngreso = new ArrayList<>();
        public int consultasDeCategorias;
        public CatalogoDeVenta producto(Producto base, Producto... adiciones) {
            productos.add(new ProductoDelMenu(base, List.of(adiciones)));
            return this;
        }
        public CatalogoDeVenta categoriaIngreso(Categoria c) { categoriasIngreso.add(c); return this; }
        @Override public List<ProductoDelMenu> productosParaVender() { return List.copyOf(productos); }
        @Override
        public List<Categoria> categoriasDeIngresoActivas() {
            consultasDeCategorias++;
            return List.copyOf(categoriasIngreso);
        }
    }

    /** Cafeterías activas; las que no estén en el conjunto se tratan como inactivas (o no visibles). */
    public static class Cafeterias implements CafeteriaRepository {
        public final Set<UUID> activas = new HashSet<>();
        public Cafeterias activa(UUID id) { activas.add(id); return this; }
        @Override public boolean estaActiva(UUID id) { return activas.contains(id); }
    }

    /** Devuelve los totales que se le configuren, como haría la BD con SUM, y recuerda el último filtro. */
    public static class Movimientos implements MovimientoRepository {
        public final List<Movimiento> datos = new ArrayList<>();
        public BigDecimal totalIngresos = BigDecimal.ZERO;
        public BigDecimal totalEgresos = BigDecimal.ZERO;
        public long totalMovimientos;
        public FiltroMovimientos ultimoFiltro;
        @Override
        public ResultadoMovimientos buscar(FiltroMovimientos filtro) {
            ultimoFiltro = filtro;
            List<Movimiento> pagina = datos.stream()
                    .filter(m -> filtro.tipo() == null || m.tipo() == filtro.tipo())
                    .skip(filtro.desplazamiento())
                    .limit(filtro.tamano())
                    .toList();
            return new ResultadoMovimientos(pagina, totalIngresos, totalEgresos, totalMovimientos);
        }
    }

    /**
     * Ejecuta la operación directamente y recuerda con qué cafetería o identidad se llamó.
     * Como PgUnidadDeTrabajo, al fijar una borra la otra.
     */
    public static class UnidadDeTrabajoDirecta implements UnidadDeTrabajo {
        public UUID ultimaCafeteria;
        public String ultimaIdentidad;
        @Override
        public <T> T ejecutar(UUID idCafeteria, Supplier<T> operacion) {
            ultimaCafeteria = idCafeteria;
            ultimaIdentidad = null;
            return operacion.get();
        }
        @Override
        public <T> T ejecutarConIdentidad(String firebaseUid, Supplier<T> operacion) {
            ultimaIdentidad = firebaseUid;
            ultimaCafeteria = null;
            return operacion.get();
        }
    }

    /**
     * Membresías (tabla usuarios) con el RLS imitado: buscarActivoPorFirebaseUid solo ve la cafetería
     * fijada en la unidad de trabajo y buscarCafeteriasDe solo funciona con la identidad fijada.
     */
    public static class Membresias implements MembresiaRepository {
        private record Fila(String uid, UsuarioAutenticado usuario, String nombreCafeteria, boolean activa,
                            boolean cafeteriaActiva) { }

        private final UnidadDeTrabajoDirecta unidadDeTrabajo;
        private final List<Fila> filas = new ArrayList<>();

        public Membresias(UnidadDeTrabajoDirecta unidadDeTrabajo) { this.unidadDeTrabajo = unidadDeTrabajo; }

        public Membresias activa(String uid, UsuarioAutenticado usuario, String nombreCafeteria) {
            filas.add(new Fila(uid, usuario, nombreCafeteria, true, true));
            return this;
        }
        public Membresias inactiva(String uid, UsuarioAutenticado usuario, String nombreCafeteria) {
            filas.add(new Fila(uid, usuario, nombreCafeteria, false, true));
            return this;
        }
        public Membresias enCafeteriaInactiva(String uid, UsuarioAutenticado usuario, String nombreCafeteria) {
            filas.add(new Fila(uid, usuario, nombreCafeteria, true, false));
            return this;
        }

        @Override
        public Optional<UsuarioAutenticado> buscarActivoPorFirebaseUid(String uid) {
            return filas.stream()
                    .filter(f -> f.activa() && f.uid().equals(uid)
                            && f.usuario().idCafeteria().equals(unidadDeTrabajo.ultimaCafeteria))
                    .map(Fila::usuario)
                    .findFirst();
        }

        @Override
        public List<CafeteriaDelUsuario> buscarCafeteriasDe(String uid) {
            return filas.stream()
                    .filter(f -> f.activa() && f.uid().equals(uid) && uid.equals(unidadDeTrabajo.ultimaIdentidad))
                    .map(f -> new CafeteriaDelUsuario(f.usuario().idCafeteria(), f.nombreCafeteria(),
                            f.usuario().rol(), f.cafeteriaActiva()))
                    .toList();
        }
    }
}
