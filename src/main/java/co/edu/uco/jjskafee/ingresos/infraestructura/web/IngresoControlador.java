package co.edu.uco.jjskafee.ingresos.infraestructura.web;

import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.ingresos.aplicacion.puertos.entrada.RegistrarIngresoCasoUso;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/** Ingresos (ventas). Cajero y administrador pueden vender. */
@RestController
@RequestMapping("/api/ingresos")
public class IngresoControlador {

    private final RegistrarIngresoCasoUso registrarIngreso;

    public IngresoControlador(RegistrarIngresoCasoUso registrarIngreso) {
        this.registrarIngreso = Objects.requireNonNull(registrarIngreso);
    }

    /** POST /api/ingresos → 201 con la venta registrada y su total calculado en el servidor. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IngresoResponse registrar(@Valid @RequestBody IngresoRequest solicitud, UsuarioAutenticado usuario) {
        return IngresoResponse.desde(registrarIngreso.registrar(solicitud.aComando(), usuario));
    }

    /** POST /api/ingresos/{idVenta}/deshacer → solo quien la registró, dentro de 60 s (ADR-0008). */
    @PostMapping("/{idVenta}/deshacer")
    public IngresoResponse deshacer(@PathVariable UUID idVenta, UsuarioAutenticado usuario) {
        return IngresoResponse.desde(registrarIngreso.deshacer(idVenta, usuario));
    }
}
