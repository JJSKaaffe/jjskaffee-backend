package co.edu.uco.jjskafee.infraestructura.output.repositorios.estructurados;

import co.edu.uco.jjskafee.dominio.DetalleVenta;
import co.edu.uco.jjskafee.dominio.Producto;
import co.edu.uco.jjskafee.dominio.Venta;
import co.edu.uco.jjskafee.dominio.enums.EstadoVenta;
import co.edu.uco.jjskafee.dominio.enums.TipoProducto;
import co.edu.uco.jjskafee.servicios.output.ports.VentaRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static co.edu.uco.jjskafee.infraestructura.output.repositorios.estructurados.ConversionesSql.aTimestamp;
import static co.edu.uco.jjskafee.infraestructura.output.repositorios.estructurados.ConversionesSql.leerInstante;

/**
 * Guarda y lee ventas (tablas venta y detalle_venta). Debe usarse dentro de
 * UnidadDeTrabajo: así la venta y sus detalles se guardan juntos o no se guarda nada.
 */
public class PgVentaRepository implements VentaRepository {

    private final JdbcTemplate jdbc;

    public PgVentaRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public void guardar(Venta venta) {
        jdbc.update("""
                        INSERT INTO venta (id, id_cafeteria, id_cajero, fecha_hora, estado, motivo_anulacion, total)
                        VALUES (?, ?, ?, ?, ?, ?, ?)""",
                venta.getId(), venta.getIdCafeteria(), venta.getIdCajero(), aTimestamp(venta.getFechaHora()),
                venta.getEstado().name(), venta.getMotivoAnulacion(), venta.total());

        // Los detalles vienen en orden (cada base antes que sus adiciones), así la FK id_detalle_base se cumple
        List<Object[]> filas = venta.getDetalles().stream()
                .map(d -> new Object[]{d.getId(), venta.getIdCafeteria(), venta.getId(), d.getProducto().getId(),
                        d.getCantidad(), d.getPrecioUnitario(),
                        d.getDetalleBase() == null ? null : d.getDetalleBase().getId()})
                .toList();
        jdbc.batchUpdate("""
                INSERT INTO detalle_venta (id, id_cafeteria, id_venta, id_producto, cantidad, precio_unitario,
                                           id_detalle_base)
                VALUES (?, ?, ?, ?, ?, ?, ?)""", filas);
    }

    @Override
    public Optional<Venta> buscarPorId(UUID idVenta) {
        Optional<FilaVenta> fila = jdbc.query("""
                        SELECT id, id_cafeteria, id_cajero, fecha_hora, estado, motivo_anulacion
                        FROM venta
                        WHERE id = ?""",
                (rs, n) -> new FilaVenta(
                        rs.getObject("id", UUID.class),
                        rs.getObject("id_cafeteria", UUID.class),
                        rs.getObject("id_cajero", UUID.class),
                        leerInstante(rs, "fecha_hora"),
                        EstadoVenta.valueOf(rs.getString("estado")),
                        rs.getString("motivo_anulacion")),
                idVenta).stream().findFirst();

        return fila.map(v -> Venta.reconstruir(v.id(), v.idCafeteria(), v.idCajero(), v.fechaHora(),
                leerDetalles(v.id()), v.estado(), v.motivoAnulacion()));
    }

    @Override
    public void actualizarEstado(Venta venta) {
        jdbc.update("UPDATE venta SET estado = ?, motivo_anulacion = ? WHERE id = ?",
                venta.getEstado().name(), venta.getMotivoAnulacion(), venta.getId());
    }

    /** Lee los detalles con el precio del momento de la venta; primero los bases para enlazar sus adiciones. */
    private List<DetalleVenta> leerDetalles(UUID idVenta) {
        List<FilaDetalle> filas = jdbc.query("""
                        SELECT d.id, d.cantidad, d.precio_unitario, d.id_detalle_base,
                               p.id AS id_producto, p.nombre, p.precio, p.tipo, p.disponible
                        FROM detalle_venta d
                        JOIN producto p ON p.id_cafeteria = d.id_cafeteria AND p.id = d.id_producto
                        WHERE d.id_venta = ?
                        ORDER BY d.id_detalle_base NULLS FIRST""",
                (rs, n) -> new FilaDetalle(
                        rs.getObject("id", UUID.class),
                        new Producto(rs.getObject("id_producto", UUID.class), rs.getString("nombre"),
                                rs.getBigDecimal("precio"), TipoProducto.valueOf(rs.getString("tipo")),
                                rs.getBoolean("disponible")),
                        rs.getInt("cantidad"),
                        rs.getBigDecimal("precio_unitario"),
                        rs.getObject("id_detalle_base", UUID.class)),
                idVenta);

        Map<UUID, DetalleVenta> porId = new HashMap<>();
        List<DetalleVenta> detalles = new ArrayList<>();
        for (FilaDetalle f : filas) {
            DetalleVenta detalle = new DetalleVenta(f.id(), f.producto(), f.cantidad(), f.precioUnitario(),
                    f.idDetalleBase() == null ? null : porId.get(f.idDetalleBase()));
            porId.put(f.id(), detalle);
            detalles.add(detalle);
        }
        return detalles;
    }

    private record FilaVenta(UUID id, UUID idCafeteria, UUID idCajero, Instant fechaHora,
                             EstadoVenta estado, String motivoAnulacion) { }

    private record FilaDetalle(UUID id, Producto producto, int cantidad, BigDecimal precioUnitario,
                               UUID idDetalleBase) { }
}
