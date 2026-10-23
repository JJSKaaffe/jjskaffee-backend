package co.edu.uco.jjskafee.ingresos.infraestructura.output.repositorios.estructurados;

import co.edu.uco.jjskafee.compartido.dominio.Producto;
import co.edu.uco.jjskafee.compartido.dominio.enums.TipoProducto;
import co.edu.uco.jjskafee.ingresos.dominio.DetalleVenta;
import co.edu.uco.jjskafee.ingresos.dominio.Venta;
import co.edu.uco.jjskafee.ingresos.dominio.enums.EstadoVenta;
import co.edu.uco.jjskafee.ingresos.servicios.output.ports.VentaRepository;
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

import static co.edu.uco.jjskafee.compartido.infraestructura.output.repositorios.estructurados.ConversionesSql.aTimestamp;
import static co.edu.uco.jjskafee.compartido.infraestructura.output.repositorios.estructurados.ConversionesSql.leerInstante;

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
    public boolean guardar(Venta venta) {
        // ON CONFLICT: si otra transacción ya guardó una venta con la misma clave de idempotencia en esta
        // cafetería, PostgreSQL espera a que termine y no inserta nada (sin error que aborte la transacción).
        // Las ventas sin clave (NULL) nunca chocan.
        int insertadas = jdbc.update("""
                        INSERT INTO venta (id, id_cafeteria, id_cajero, fecha_hora, estado, motivo_anulacion, total,
                                           clave_idempotencia, hash_solicitud)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (id_cafeteria, clave_idempotencia) DO NOTHING""",
                venta.getId(), venta.getIdCafeteria(), venta.getIdCajero(), aTimestamp(venta.getFechaHora()),
                venta.getEstado().name(), venta.getMotivoAnulacion(), venta.total(),
                venta.getClaveIdempotencia(), venta.getHashSolicitud());
        if (insertadas == 0) {
            return false;
        }

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
        return true;
    }

    @Override
    public Optional<Venta> buscarPorId(UUID idVenta) {
        return buscarUna("""
                SELECT id, id_cafeteria, id_cajero, fecha_hora, estado, motivo_anulacion,
                       clave_idempotencia, hash_solicitud
                FROM venta
                WHERE id = ?""", idVenta);
    }

    @Override
    public Optional<Venta> buscarPorClaveIdempotencia(UUID claveIdempotencia) {
        // RLS limita la búsqueda a la cafetería activa: la UNIQUE es (id_cafeteria, clave_idempotencia)
        return buscarUna("""
                SELECT id, id_cafeteria, id_cajero, fecha_hora, estado, motivo_anulacion,
                       clave_idempotencia, hash_solicitud
                FROM venta
                WHERE clave_idempotencia = ?""", claveIdempotencia);
    }

    private Optional<Venta> buscarUna(String sql, UUID valor) {
        Optional<FilaVenta> fila = jdbc.query(sql,
                (rs, n) -> new FilaVenta(
                        rs.getObject("id", UUID.class),
                        rs.getObject("id_cafeteria", UUID.class),
                        rs.getObject("id_cajero", UUID.class),
                        leerInstante(rs, "fecha_hora"),
                        EstadoVenta.valueOf(rs.getString("estado")),
                        rs.getString("motivo_anulacion"),
                        rs.getObject("clave_idempotencia", UUID.class),
                        rs.getString("hash_solicitud")),
                valor).stream().findFirst();

        return fila.map(v -> Venta.reconstruir(v.id(), v.idCafeteria(), v.idCajero(), v.fechaHora(),
                leerDetalles(v.id()), v.claveIdempotencia(), v.hashSolicitud(), v.estado(), v.motivoAnulacion()));
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
                             EstadoVenta estado, String motivoAnulacion,
                             UUID claveIdempotencia, String hashSolicitud) { }

    private record FilaDetalle(UUID id, Producto producto, int cantidad, BigDecimal precioUnitario,
                               UUID idDetalleBase) { }
}
