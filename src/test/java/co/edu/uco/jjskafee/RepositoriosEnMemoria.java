package co.edu.uco.jjskafee;

import co.edu.uco.jjskafee.compartido.dominio.Categoria;
import co.edu.uco.jjskafee.compartido.dominio.Producto;
import co.edu.uco.jjskafee.compartido.servicios.output.ports.CatalogoRepository;
import co.edu.uco.jjskafee.compartido.servicios.output.ports.UnidadDeTrabajo;
import co.edu.uco.jjskafee.egresos.dominio.Egreso;
import co.edu.uco.jjskafee.egresos.servicios.output.ports.EgresoRepository;
import co.edu.uco.jjskafee.ingresos.dominio.Venta;
import co.edu.uco.jjskafee.ingresos.servicios.output.ports.CafeteriaRepository;
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

    /** Ejecuta la operación directamente y recuerda con qué cafetería se llamó. */
    public static class UnidadDeTrabajoDirecta implements UnidadDeTrabajo {
        public UUID ultimaCafeteria;
        @Override
        public <T> T ejecutar(UUID idCafeteria, Supplier<T> operacion) {
            ultimaCafeteria = idCafeteria;
            return operacion.get();
        }
    }
}
