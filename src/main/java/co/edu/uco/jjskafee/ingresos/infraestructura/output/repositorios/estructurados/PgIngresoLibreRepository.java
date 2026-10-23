package co.edu.uco.jjskafee.ingresos.infraestructura.output.repositorios.estructurados;

import co.edu.uco.jjskafee.compartido.dominio.Categoria;
import co.edu.uco.jjskafee.compartido.dominio.enums.TipoCategoria;
import co.edu.uco.jjskafee.ingresos.dominio.IngresoLibre;
import co.edu.uco.jjskafee.ingresos.servicios.output.ports.IngresoLibreRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static co.edu.uco.jjskafee.compartido.infraestructura.output.repositorios.estructurados.ConversionesSql.aTimestamp;
import static co.edu.uco.jjskafee.compartido.infraestructura.output.repositorios.estructurados.ConversionesSql.leerInstante;

/**
 * Guarda y lee ingresos libres (tabla ingreso_libre, db/09). Debe usarse dentro de UnidadDeTrabajo:
 * la política RLS rechaza un ingreso de otra cafetería y solo deja leer los de la cafetería activa.
 */
public class PgIngresoLibreRepository implements IngresoLibreRepository {

    private static final String SELECT = """
            SELECT l.id, l.id_cafeteria, l.monto, l.descripcion, l.comprobante, l.registrado_por, l.fecha_hora,
                   l.clave_idempotencia, l.hash_solicitud, l.anulado, l.motivo_anulacion,
                   c.id AS id_categoria, c.nombre AS categoria, c.tipo AS tipo_categoria, c.archivada
            FROM ingreso_libre l
            JOIN categoria c ON c.id_cafeteria = l.id_cafeteria AND c.id = l.id_categoria
            """;

    private final JdbcTemplate jdbc;

    public PgIngresoLibreRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public boolean guardar(IngresoLibre ingreso) {
        // ON CONFLICT: si otra transacción ya guardó un ingreso con la misma clave de idempotencia en esta
        // cafetería, PostgreSQL espera a que termine y no inserta nada (sin error que aborte la transacción).
        // Los ingresos sin clave (NULL) nunca chocan.
        int insertados = jdbc.update("""
                        INSERT INTO ingreso_libre (id, id_cafeteria, id_categoria, monto, descripcion, comprobante,
                                                   registrado_por, fecha_hora, clave_idempotencia, hash_solicitud)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (id_cafeteria, clave_idempotencia) DO NOTHING""",
                ingreso.getId(), ingreso.getIdCafeteria(), ingreso.getCategoria().getId(), ingreso.getMonto(),
                ingreso.getDescripcion(), ingreso.getComprobante(), ingreso.getRegistradoPor(),
                aTimestamp(ingreso.getFechaHora()), ingreso.getClaveIdempotencia(), ingreso.getHashSolicitud());
        return insertados == 1;
    }

    @Override
    public Optional<IngresoLibre> buscarPorId(UUID idIngreso) {
        return buscarUno(SELECT + "WHERE l.id = ?", idIngreso);
    }

    @Override
    public Optional<IngresoLibre> buscarPorClaveIdempotencia(UUID claveIdempotencia) {
        // RLS limita la búsqueda a la cafetería activa: la UNIQUE es (id_cafeteria, clave_idempotencia)
        return buscarUno(SELECT + "WHERE l.clave_idempotencia = ?", claveIdempotencia);
    }

    private Optional<IngresoLibre> buscarUno(String sql, UUID valor) {
        return jdbc.query(sql,
                (rs, n) -> IngresoLibre.reconstruir(
                        rs.getObject("id", UUID.class),
                        rs.getObject("id_cafeteria", UUID.class),
                        new Categoria(rs.getObject("id_categoria", UUID.class), rs.getString("categoria"),
                                TipoCategoria.valueOf(rs.getString("tipo_categoria")), rs.getBoolean("archivada")),
                        rs.getBigDecimal("monto"),
                        rs.getString("descripcion"),
                        rs.getString("comprobante"),
                        rs.getObject("registrado_por", UUID.class),
                        leerInstante(rs, "fecha_hora"),
                        rs.getObject("clave_idempotencia", UUID.class),
                        rs.getString("hash_solicitud"),
                        rs.getBoolean("anulado"),
                        rs.getString("motivo_anulacion")),
                valor).stream().findFirst();
    }
}
