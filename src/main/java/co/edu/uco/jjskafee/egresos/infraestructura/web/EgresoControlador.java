package co.edu.uco.jjskafee.egresos.infraestructura.web;

import co.edu.uco.jjskafee.compartido.dominio.UsuarioAutenticado;
import co.edu.uco.jjskafee.egresos.aplicacion.puertos.entrada.RegistrarEgresoCasoUso;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/** Egresos. Solo el administrador (lo valida el caso de uso, no el frontend · EXP04-01). */
@RestController
@RequestMapping("/api/egresos")
public class EgresoControlador {

    private final RegistrarEgresoCasoUso registrarEgreso;

    public EgresoControlador(RegistrarEgresoCasoUso registrarEgreso) {
        this.registrarEgreso = Objects.requireNonNull(registrarEgreso);
    }

    /** POST /api/egresos → 201 con el egreso registrado. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EgresoResponse registrar(@Valid @RequestBody EgresoRequest solicitud, UsuarioAutenticado usuario) {
        return EgresoResponse.desde(registrarEgreso.registrar(solicitud.aComando(), usuario));
    }
}
