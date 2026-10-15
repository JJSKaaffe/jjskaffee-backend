package co.edu.uco.jjskafee.movimientos.infraestructura.web;

import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.movimientos.aplicacion.puertos.entrada.ConsultarMovimientosCasoUso;
import co.edu.uco.jjskafee.movimientos.dominio.FiltroMovimientos;
import co.edu.uco.jjskafee.movimientos.dominio.TipoMovimiento;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Consulta de ingresos y egresos. El administrador ve todo; el cajero, solo lo suyo (RF9). */
@RestController
@RequestMapping("/api/movimientos")
public class MovimientoControlador {

    private final ConsultarMovimientosCasoUso consultarMovimientos;

    public MovimientoControlador(ConsultarMovimientosCasoUso consultarMovimientos) {
        this.consultarMovimientos = Objects.requireNonNull(consultarMovimientos);
    }

    /**
     * GET /api/movimientos?desde=2026-10-01&hasta=2026-10-14&tipo=EGRESO&idCategoria=...&pagina=0&tamano=50
     * Todos los parámetros son opcionales. Las fechas son días en UTC-0.
     */
    @GetMapping
    public MovimientosResponse consultar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) TipoMovimiento tipo,
            @RequestParam(required = false) UUID idCategoria,
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamano,
            UsuarioAutenticado usuario) {
        FiltroMovimientos filtro = FiltroMovimientos.crear(desde, hasta, tipo, idCategoria, pagina, tamano);
        return MovimientosResponse.desde(consultarMovimientos.consultar(filtro, usuario));
    }
}
