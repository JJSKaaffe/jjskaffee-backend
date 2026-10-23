package co.edu.uco.jjskafee.compartido.dominio;

import co.edu.uco.jjskafee.compartido.dominio.enums.TipoCategoria;

import java.util.Objects;
import java.util.UUID;

/** Categoría de ingresos, egresos o del menú (RF18). */
public class Categoria {

    private final UUID id;
    private final String nombre;
    private final TipoCategoria tipo;
    private final boolean archivada;

    public Categoria(UUID id, String nombre, TipoCategoria tipo, boolean archivada) {
        this.id = Objects.requireNonNull(id, "El id de la categoría es obligatorio");
        this.nombre = Objects.requireNonNull(nombre, "El nombre de la categoría es obligatorio");
        this.tipo = Objects.requireNonNull(tipo, "El tipo de la categoría es obligatorio");
        this.archivada = archivada;
    }

    /** Un egreso solo se puede registrar en una categoría de tipo EGRESO que no esté archivada. */
    public boolean esEgresoActiva() {
        return tipo == TipoCategoria.EGRESO && !archivada;
    }

    /** Un ingreso libre solo se puede registrar en una categoría de tipo INGRESO que no esté archivada. */
    public boolean esIngresoActiva() {
        return tipo == TipoCategoria.INGRESO && !archivada;
    }

    public UUID getId() { return id; }
    public String getNombre() { return nombre; }
    public TipoCategoria getTipo() { return tipo; }
    public boolean isArchivada() { return archivada; }
}
