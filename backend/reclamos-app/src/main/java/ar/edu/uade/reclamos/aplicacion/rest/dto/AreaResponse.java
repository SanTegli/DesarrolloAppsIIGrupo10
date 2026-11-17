package ar.edu.uade.reclamos.aplicacion.rest.dto;

import java.util.List;

/** Un área atiende sus categorías en su municipio, que es su jurisdicción. */
public record AreaResponse(Long id, String nombre, boolean activa, ReferenciaResponse municipio,
                           List<ReferenciaResponse> categorias) { }
