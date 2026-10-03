package ar.edu.uade.reclamos.aplicacion.rest.dto;

import java.util.List;

public record AreaResponse(Long id, String nombre, boolean activa,
                           List<ReferenciaResponse> categorias, List<ReferenciaResponse> barrios) { }
