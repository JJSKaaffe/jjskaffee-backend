package co.edu.uco.jjskafee.catalogo.infraestructura.web;

import co.edu.uco.jjskafee.catalogo.dominio.Categoria;

import java.util.UUID;

public record CategoriaResponse(UUID idCategoria, String nombre, String tipo) {

    public static CategoriaResponse desde(Categoria c) {
        return new CategoriaResponse(c.id(), c.nombre(), c.tipo().name());
    }
}
