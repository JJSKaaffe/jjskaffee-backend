package co.edu.uco.jjskafee.ingresos.infraestructura.persistencia;

import co.edu.uco.jjskafee.compartido.dominio.excepciones.ReglaNegocioException;
import co.edu.uco.jjskafee.ingresos.aplicacion.puertos.salida.RepositorioVentas;
import co.edu.uco.jjskafee.ingresos.dominio.DetalleVenta;
import co.edu.uco.jjskafee.ingresos.dominio.EstadoVenta;
import co.edu.uco.jjskafee.ingresos.dominio.MensajesIngresos;
import co.edu.uco.jjskafee.ingresos.dominio.Venta;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Tablas venta y detalle_venta con JDBC. Debe llamarse dentro de la UnidadDeTrabajo:
 * así la venta y sus líneas se guardan juntas (ADR-0002) y con RLS activo.
 * La columna detalle_venta.subtotal la calcula la BD (GENERATED ALWAYS).
 */
public class PgRepositorioVentas implements RepositorioVentas {

    private static final String SQL_INSERTAR_VENTA = """
            INSERT INTO venta (id_venta, id_cafeteria, id_cajero, fecha_hora, total, estado)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_INSERTAR_DETALLE = """
            INSERT INTO detalle_venta (id_detalle, id_cafeteria, id_venta, id_producto, id_detalle_base,
                                       cantidad, precio_unitario)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    /** FOR UPDATE bloquea la fila: dos "deshacer" al mismo tiempo no pueden pisarse. */
    private static final String SQL_ENCABEZADO_PARA_ANULAR = """
            SELECT id_venta, id_cafeteria, id_cajero, fecha_hora, estado,
                   motivo_anulacion, usuario_autorizador, anulada_en
              FROM venta
             WHERE id_cafeteria = ? AND id_venta = ?
               FOR UPDATE
            """;

    private static final String SQL_ANULAR = """
            UPDATE venta
               SET estado = ?, motivo_anulacion = ?, usuario_autorizador = ?, anulada_en = ?
             WHERE id_cafeteria = ? AND id_venta = ? AND estado = 'CONFIRMADA'
            """;

    private final JdbcTemplate jdbc;

    public PgRepositorioVentas(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public void guardar(Venta venta) {
        jdbc.update(SQL_INSERTAR_VENTA, venta.getId(), venta.getIdCafeteria(), venta.getIdCajero(),
                utc(venta.getFechaHora()), venta.total(), venta.getEstado().name());

        // Las líneas base van antes que sus adiciones (FK id_detalle_base): el dominio las arma en ese orden
        List<Object[]> filas = venta.getDetalles().stream().map(d -> new Object[]{
                d.getId(),
                venta.getIdCafeteria(),
                venta.getId(),
                d.getProducto().id(),
                new SqlParameterValue(Types.OTHER, idDetalleBase(d)),
                d.getCantidad(),
                d.getPrecioUnitario()
        }).toList();
        jdbc.batchUpdate(SQL_INSERTAR_DETALLE, filas);
    }

    @Override
    public Optional<Venta> buscarParaAnular(UUID idCafeteria, UUID idVenta) {
        return jdbc.query(SQL_ENCABEZADO_PARA_ANULAR, (fila, i) -> Venta.reconstruirEncabezado(
                        fila.getObject("id_venta", UUID.class),
                        fila.getObject("id_cafeteria", UUID.class),
                        fila.getObject("id_cajero", UUID.class),
                        instante(fila.getObject("fecha_hora", OffsetDateTime.class)),
                        EstadoVenta.valueOf(fila.getString("estado")),
                        fila.getString("motivo_anulacion"),
                        fila.getObject("usuario_autorizador", UUID.class),
                        instante(fila.getObject("anulada_en", OffsetDateTime.class))),
                idCafeteria, idVenta).stream().findFirst();
    }

    @Override
    public void registrarAnulacion(Venta venta) {
        int filas = jdbc.update(SQL_ANULAR, venta.getEstado().name(), venta.getMotivoAnulacion(),
                venta.getAnuladaPor(), utc(venta.getAnuladaEn()), venta.getIdCafeteria(), venta.getId());
        if (filas == 0) {
            throw new ReglaNegocioException(MensajesIngresos.VENTA_YA_ANULADA);
        }
    }

    private static UUID idDetalleBase(DetalleVenta detalle) {
        return detalle.esAdicion() ? detalle.getDetalleBase().getId() : null;
    }

    private static OffsetDateTime utc(Instant instante) {
        return instante == null ? null : instante.atOffset(ZoneOffset.UTC);
    }

    private static Instant instante(OffsetDateTime fecha) {
        return fecha == null ? null : fecha.toInstant();
    }
}
