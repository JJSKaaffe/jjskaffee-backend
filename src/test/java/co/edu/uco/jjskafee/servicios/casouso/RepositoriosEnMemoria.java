package co.edu.uco.jjskafee.servicios.casouso;

import co.edu.uco.jjskafee.dominio.Categoria;
import co.edu.uco.jjskafee.dominio.Egreso;
import co.edu.uco.jjskafee.dominio.FiltroMovimientos;
import co.edu.uco.jjskafee.dominio.Movimiento;
import co.edu.uco.jjskafee.dominio.Producto;
import co.edu.uco.jjskafee.dominio.ResultadoMovimientos;
import co.edu.uco.jjskafee.dominio.Venta;
import co.edu.uco.jjskafee.servicios.output.ports.CatalogoRepository;
import co.edu.uco.jjskafee.servicios.output.ports.EgresoRepository;
import co.edu.uco.jjskafee.servicios.output.ports.MovimientoRepository;
import co.edu.uco.jjskafee.servicios.output.ports.UnidadDeTrabajo;
import co.edu.uco.jjskafee.servicios.output.ports.VentaRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Implementaciones falsas de los puertos de salida, en memoria.
 * Gracias a la hexagonal, los casos de uso se prueban sin base de datos ni Spring.
 */
final class RepositoriosEnMemoria {

    private RepositoriosEnMemoria() { }

    static class Catalogo implements CatalogoRepository {
        final Map<UUID, Producto> productos = new HashMap<>();
        final Map<UUID, Set<UUID>> adiciones = new HashMap<>();
        final Map<UUID, Categoria> categorias = new HashMap<>();

        Catalogo producto(Producto p) { productos.put(p.getId(), p); return this; }
        Catalogo adiciones(Producto base, Set<UUID> ids) { adiciones.put(base.getId(), ids); return this; }
        Catalogo categoria(Categoria c) { categorias.put(c.getId(), c); return this; }

        @Override public Optional<Producto> buscarProducto(UUID id) { return Optional.ofNullable(productos.get(id)); }
        @Override public Set<UUID> adicionesPermitidas(UUID idBase) { return adiciones.getOrDefault(idBase, Set.of()); }
        @Override public Optional<Categoria> buscarCategoria(UUID id) { return Optional.ofNullable(categorias.get(id)); }
    }

    static class Egresos implements EgresoRepository {
        final List<Egreso> guardados = new ArrayList<>();
        @Override public void guardar(Egreso egreso) { guardados.add(egreso); }
    }

    static class Ventas implements VentaRepository {
        final Map<UUID, Venta> guardadas = new HashMap<>();
        @Override public void guardar(Venta venta) { guardadas.put(venta.getId(), venta); }
        @Override public Optional<Venta> buscarPorId(UUID id) { return Optional.ofNullable(guardadas.get(id)); }
        @Override public void actualizarEstado(Venta venta) { guardadas.put(venta.getId(), venta); }
    }

    /** Devuelve los totales que se le configuren, como haría la BD con SUM, y recuerda el último filtro. */
    static class Movimientos implements MovimientoRepository {
        final List<Movimiento> datos = new ArrayList<>();
        BigDecimal totalIngresos = BigDecimal.ZERO;
        BigDecimal totalEgresos = BigDecimal.ZERO;
        long totalMovimientos;
        FiltroMovimientos ultimoFiltro;
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
    static class UnidadDeTrabajoDirecta implements UnidadDeTrabajo {
        UUID ultimaCafeteria;
        @Override
        public <T> T ejecutar(UUID idCafeteria, Supplier<T> operacion) {
            ultimaCafeteria = idCafeteria;
            return operacion.get();
        }
    }
}
